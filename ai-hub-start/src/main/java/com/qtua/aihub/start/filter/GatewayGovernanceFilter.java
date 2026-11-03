package com.qtua.aihub.start.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.res.ErrorResponse;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.service.gateway.GatewayGovernanceService;
import com.qtua.aihub.service.gateway.model.GatewayContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 网关治理过滤器。
 *
 * <p>限流、预算、计价、spend、访问日志的统一入口，与具体业务接口无关。
 * 请求前预占，请求后按业务回填的 usage 结算；失败释放。
 * 未来新增流式/文件接口时，只需扩展请求提取与 usage 回填，治理主流程复用。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class GatewayGovernanceFilter extends OncePerRequestFilter {

    private static final String CHAT_PATH_V1 = "/v1/chat/completions";
    private static final String CHAT_PATH = "/chat/completions";
    private static final String MCP_CALL_PREFIX = "/v1/mcp/tools/";
    private static final String MCP_CALL_SUFFIX = ":call";
    private static final String PROMPT_PREFIX = "/v1/prompts/";
    private static final String PROMPT_CHAT_SUFFIX = "/chat";
    private static final String MCP_SERVER_PATH = "/mcp";
    private static final String UTF_8 = "UTF-8";
    private static final String CALL_TYPE_MCP = "MCP";
    private static final String CALL_TYPE_PROMPT = "PROMPT";

    private final GatewayGovernanceService governanceService;
    private final ObjectMapper objectMapper;

    /**
     * 构造过滤器。
     *
     * @param governanceService 网关治理服务
     * @param objectMapper      JSON 序列化器（错误响应）
     */
    public GatewayGovernanceFilter(GatewayGovernanceService governanceService,
                                   ObjectMapper objectMapper) {
        this.governanceService = governanceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 治理聊天补全与 MCP 工具调用入口；后续流式/文件接口在此扩展。
     *
     * @param request HTTP 请求
     * @return true 表示跳过治理
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String relative = relativePath(request);
        if (CHAT_PATH_V1.equals(relative) || CHAT_PATH.equals(relative)) {
            return false;
        }
        if (isPromptChat(relative) || MCP_SERVER_PATH.equals(relative)) {
            return false;
        }
        return !(relative.startsWith(MCP_CALL_PREFIX) && relative.endsWith(MCP_CALL_SUFFIX));
    }

    private boolean isPromptChat(String relative) {
        return relative.startsWith(PROMPT_PREFIX) && relative.endsWith(PROMPT_CHAT_SUFFIX)
                && relative.length() > PROMPT_PREFIX.length() + PROMPT_CHAT_SUFFIX.length();
    }

    /**
     * 从 MCP JSON-RPC 请求解析调用目标（tools/call 取工具名，其他取方法名）。
     *
     * @param body 请求体 JSON
     * @return 目标名（解析失败返回 "mcp"）
     */
    private String mcpServerTarget(String body) {
        try {
            if (!StringUtils.hasText(body)) {
                return "mcp";
            }
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
            String method = root.path("method").asText("mcp");
            if ("tools/call".equals(method)) {
                String toolName = root.path("params").path("name").asText("");
                return StringUtils.hasText(toolName) ? toolName : method;
            }
            return method;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return "mcp";
        }
    }

    /**
     * 从 Prompt 对话路径解析模板名。
     *
     * @param request HTTP 请求
     * @return 模板名
     */
    private String promptName(HttpServletRequest request) {
        String relative = relativePath(request);
        return relative.substring(PROMPT_PREFIX.length(),
                relative.length() - PROMPT_CHAT_SUFFIX.length());
    }

    private String relativePath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        return path.startsWith(contextPath) ? path.substring(contextPath.length()) : path;
    }

    /**
     * 从 MCP 调用路径解析对外工具名（去掉前缀与 ":call" 后缀）。
     *
     * @param request HTTP 请求
     * @return 工具名
     */
    private String mcpToolName(HttpServletRequest request) {
        String relative = relativePath(request);
        return relative.substring(MCP_CALL_PREFIX.length(),
                relative.length() - MCP_CALL_SUFFIX.length());
    }

    /**
     * 执行治理逻辑。
     *
     * @param request     HTTP 请求
     * @param response    HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        KeyAuthDTO auth = (KeyAuthDTO) request.getAttribute(AuthFilter.KEY_AUTH_ATTRIBUTE);
        if (ObjectUtils.isEmpty(auth)) {
            filterChain.doFilter(request, response);
            return;
        }
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE);
        GatewayContext context = new GatewayContext(requestId, auth.keyHash(),
                request.getRequestURI(), auth);
        CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(request);
        String relative = relativePath(request);
        if (relative.startsWith(MCP_CALL_PREFIX) && relative.endsWith(MCP_CALL_SUFFIX)) {
            context.setCallTarget(CALL_TYPE_MCP, mcpToolName(request));
        } else if (MCP_SERVER_PATH.equals(relative)) {
            // JSON-RPC 目标从请求体解析（tools/call 取工具名）。
            context.setCallTarget(CALL_TYPE_MCP, mcpServerTarget(wrapped.getBody()));
        } else if (isPromptChat(relative)) {
            // targetName 先置模板名；Controller 渲染后回填 name@vN。
            context.setCallTarget(CALL_TYPE_PROMPT, promptName(request));
        }
        wrapped.setAttribute(GatewayContext.ATTRIBUTE, context);
        try {
            governanceService.reserve(context, wrapped.getBody());
            filterChain.doFilter(wrapped, response);
        } catch (ChatCompletionException ex) {
            context.fail(ex.getStatus().value(), ex.getType());
            governanceService.fail(context);
            writeError(response, ex);
            return;
        } catch (RuntimeException ex) {
            context.fail(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "internal_error");
            governanceService.fail(context);
            writeError(response, ChatCompletionException.internalError(
                    "网关治理失败", ex));
            return;
        }
        // 流式请求：响应尚未结束，usage 未知；由 Controller 在流结束回调中收尾。
        if (context.isStreaming()) {
            return;
        }
        if (context.isFailed()) {
            governanceService.fail(context);
        } else {
            governanceService.complete(context);
        }
    }

    /**
     * 写 OpenAI 兼容错误体。
     *
     * @param response HTTP 响应
     * @param ex       业务异常
     * @throws IOException IO 异常
     */
    private void writeError(HttpServletResponse response, ChatCompletionException ex)
            throws IOException {
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(UTF_8);
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(ex.getMessage(), ex.getType(), ex.getParam(), ex.getCode()));
    }

    /**
     * 可重复读取请求体的包装器（聊天 JSON 体积小；大文件接口需改用流式提取）。
     */
    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        /**
         * 读取并缓存请求体。
         *
         * @param request 原始请求
         * @throws IOException IO 异常
         */
        private CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = StreamUtils.copyToByteArray(request.getInputStream());
        }

        /**
         * 请求体文本。
         *
         * @return UTF-8 文本
         */
        private String getBody() {
            return new String(body, charset());
        }

        /**
         * 每次返回新的输入流，支持 Controller 再次读取。
         *
         * @return ServletInputStream
         */
        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream delegate = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return delegate.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return delegate.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return delegate.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("ReadListener is not supported");
                }
            };
        }

        /**
         * 每次返回新的 Reader，支持 Controller 再次读取。
         *
         * @return BufferedReader
         */
        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), charset()));
        }

        private Charset charset() {
            String encoding = getCharacterEncoding();
            return ObjectUtils.isEmpty(encoding) ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        }
    }
}
