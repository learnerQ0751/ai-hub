package com.qtua.aihub.console.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtua.aihub.api.model.res.ErrorResponse;
import com.qtua.aihub.manager.idempotency.IdempotencyKeyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 控制面写请求幂等过滤器（REQ-5.2）。
 *
 * <p>客户端携带 {@code Idempotency-Key} 时：同键同请求返回首次响应，不重复执行；
 * 同键不同请求返回 409；处理中的同键请求返回 409。记录落库，重启后仍有效。</p>
 *
 * <p>只处理 JSON 写请求；multipart（RAG 文档）由内容哈希天然去重。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class IdempotencyFilter extends OncePerRequestFilter {

    /** 幂等键请求头。 */
    public static final String HEADER = "Idempotency-Key";
    /** 重放标记响应头。 */
    public static final String REPLAY_HEADER = "X-Idempotent-Replay";

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyFilter.class);
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_CACHED_BODY = 1024 * 1024;
    private static final int HTTP_OK_MIN = 200;
    private static final int HTTP_OK_MAX = 300;
    private static final String UTF_8 = "UTF-8";

    private final IdempotencyKeyManager idempotencyManager;
    private final ObjectMapper objectMapper;

    /**
     * 构造过滤器。
     *
     * @param idempotencyManager 幂等键门面
     * @param objectMapper       JSON 序列化器
     */
    public IdempotencyFilter(IdempotencyKeyManager idempotencyManager,
                             ObjectMapper objectMapper) {
        this.idempotencyManager = idempotencyManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 仅处理带幂等键的 JSON 写请求。
     *
     * @param request HTTP 请求
     * @return true 表示跳过
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!StringUtils.hasText(request.getHeader(HEADER))) {
            return true;
        }
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        String contentType = request.getContentType();
        return ObjectUtils.isEmpty(contentType)
                || contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    /**
     * 幂等预占 → 执行 → 回填；重放直接返回首次响应。
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
        String key = request.getHeader(HEADER).trim();
        if (key.length() > MAX_KEY_LENGTH || !key.matches("[A-Za-z0-9._:-]+")) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "幂等键格式不正确",
                    "invalid_request_error", "400");
            return;
        }
        CachedBodyRequest cached = new CachedBodyRequest(request);
        String requestHash = hash(request, cached.getBody());
        if (!reserveOrReplay(key, requestHash, response)) {
            return;
        }
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cached, wrapped);
        } catch (RuntimeException | Error ex) {
            idempotencyManager.release(key);
            throw ex;
        }
        byte[] body = wrapped.getContentAsByteArray();
        int status = wrapped.getStatus();
        if (status >= HTTP_OK_MIN && status < HTTP_OK_MAX && body.length <= MAX_CACHED_BODY) {
            idempotencyManager.complete(key, status, wrapped.getContentType(),
                    new String(body, charset(request)));
        } else {
            // 失败请求释放幂等键，允许客户端修正后重试。
            idempotencyManager.release(key);
        }
        wrapped.copyBodyToResponse();
    }

    private boolean reserveOrReplay(String key, String requestHash, HttpServletResponse response)
            throws IOException {
        if (idempotencyManager.reserve(key, requestHash)) {
            return true;
        }
        IdempotencyKeyManager.Record record = idempotencyManager.find(key).orElse(null);
        if (ObjectUtils.isEmpty(record)) {
            // 记录刚被释放，允许本次执行。
            return idempotencyManager.reserve(key, requestHash);
        }
        if (!requestHash.equals(record.requestHash())) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "幂等键已用于不同的请求", "idempotency_error", "409");
            return false;
        }
        if (ObjectUtils.isEmpty(record.status())) {
            writeError(response, HttpServletResponse.SC_CONFLICT,
                    "同一幂等键的请求正在处理中，请稍后重试", "idempotency_error", "409");
            return false;
        }
        response.setStatus(record.status());
        if (StringUtils.hasText(record.contentType())) {
            response.setContentType(record.contentType());
        }
        response.setCharacterEncoding(UTF_8);
        response.setHeader(REPLAY_HEADER, "true");
        response.getWriter().write(ObjectUtils.isEmpty(record.body()) ? "" : record.body());
        LOG.info("idempotent replay: key={}", key);
        return false;
    }

    private String hash(HttpServletRequest request, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private Charset charset(HttpServletRequest request) {
        String encoding = request.getCharacterEncoding();
        return ObjectUtils.isEmpty(encoding) ? StandardCharsets.UTF_8 : Charset.forName(encoding);
    }

    private void writeError(HttpServletResponse response, int status, String message,
                            String type, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(UTF_8);
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(message, type, null, code));
    }

    /**
     * 可重复读取请求体（仅 JSON 写请求，体积小）。
     */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = StreamUtils.copyToByteArray(request.getInputStream());
        }

        private byte[] getBody() {
            return body;
        }

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

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = ObjectUtils.isEmpty(encoding) ? StandardCharsets.UTF_8
                    : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
