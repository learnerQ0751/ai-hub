package com.qtua.aihub.integration;

import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.exception.ChatCompletionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 上游 OpenAI 兼容 HTTP 客户端。
 *
 * <p>只负责单次 HTTP 调用与异常映射；路由、改写、计量都在 core/service。
 * 使用 RestClient 原样接收响应字符串，避免 DTO 序列化丢字段。</p>
 */
@Component
public class UpstreamClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int CLIENT_ERROR_MIN = 400;
    private static final int CLIENT_ERROR_MAX_EXCLUSIVE = 500;

    private final RestClient restClient;
    private final WebClient webClient;

    /**
     * 构造客户端。
     *
     * @param builder            Spring 自动配置的 RestClient.Builder（同步调用）
     * @param webClientBuilder   Spring 自动配置的 WebClient.Builder（流式调用）
     */
    public UpstreamClient(RestClient.Builder builder, WebClient.Builder webClientBuilder) {
        this.restClient = builder.build();
        this.webClient = webClientBuilder.build();
    }

    /**
     * 调用上游 /chat/completions。
     *
     * @param deployment  模型部署
     * @param requestBody 已改写的请求 JSON
     * @return 上游原始响应 JSON
     */
    public String chat(ModelDeployment deployment, String requestBody) {
        try {
            String response = restClient.post()
                    .uri(normalize(deployment.apiBase()) + CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + deployment.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            if (!StringUtils.hasText(response)) {
                throw ChatCompletionException.upstreamError("上游返回了空响应", null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            int upstreamStatus = ex.getStatusCode().value();
            if (upstreamStatus == HttpStatus.UNAUTHORIZED.value()) {
                throw ChatCompletionException.upstreamAuthError(ex);
            }
            // 上游 429：换部署可能规避，标记可重试。
            if (upstreamStatus == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw ChatCompletionException.upstreamRateLimited(ex);
            }
            // 其余 4xx：请求自身问题，重试无意义。
            if (upstreamStatus >= CLIENT_ERROR_MIN && upstreamStatus < CLIENT_ERROR_MAX_EXCLUSIVE) {
                throw ChatCompletionException.upstreamClientError(upstreamStatus, ex);
            }
            throw ChatCompletionException.upstreamError(
                    "上游请求失败，状态码 " + upstreamStatus, ex);
        } catch (ResourceAccessException ex) {
            throw ChatCompletionException.upstreamTimeout(ex);
        }
    }

    /**
     * 流式调用上游 /chat/completions（SSE）。
     *
     * <p>返回的每个元素是 SSE 的 data 内容（不含 {@code data:} 前缀）；
     * 错误响应在订阅时映射为统一的 {@link ChatCompletionException}。
     * 调用方负责取消订阅以释放上游连接。</p>
     *
     * @param deployment  模型部署
     * @param requestBody 已改写的请求 JSON（stream=true）
     * @return SSE data 流
     */
    public Flux<String> stream(ModelDeployment deployment, String requestBody) {
        return webClient.post()
                .uri(normalize(deployment.apiBase()) + CHAT_COMPLETIONS_PATH)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + deployment.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.<ChatCompletionException>error(
                                mapStreamError(response.statusCode().value()))))
                .bodyToFlux(String.class)
                .onErrorMap(WebClientRequestException.class, ChatCompletionException::upstreamTimeout);
    }

    /**
     * 流式请求的错误映射；分类规则与同步调用一致。
     *
     * @param upstreamStatus 上游状态码
     * @return 业务异常
     */
    private ChatCompletionException mapStreamError(int upstreamStatus) {
        Throwable cause = new IllegalStateException("upstream status " + upstreamStatus);
        if (upstreamStatus == HttpStatus.UNAUTHORIZED.value()) {
            return ChatCompletionException.upstreamAuthError(cause);
        }
        if (upstreamStatus == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return ChatCompletionException.upstreamRateLimited(cause);
        }
        if (upstreamStatus >= CLIENT_ERROR_MIN && upstreamStatus < CLIENT_ERROR_MAX_EXCLUSIVE) {
            return ChatCompletionException.upstreamClientError(upstreamStatus, cause);
        }
        return ChatCompletionException.upstreamError(
                "上游请求失败，状态码 " + upstreamStatus, cause);
    }

    /**
     * 去掉 api-base 末尾的斜杠，避免拼出双斜杠。
     *
     * @param apiBase 上游基础地址
     * @return 规范化地址
     */
    private String normalize(String apiBase) {
        if (apiBase.endsWith("/")) {
            return apiBase.substring(0, apiBase.length() - 1);
        }
        return apiBase;
    }
}
