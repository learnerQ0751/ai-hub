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

    private final RestClient restClient;

    /**
     * 构造客户端。
     *
     * @param builder Spring 自动配置的 RestClient.Builder
     */
    public UpstreamClient(RestClient.Builder builder) {
        this.restClient = builder.build();
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
                throw ChatCompletionException.upstreamError("Upstream returned an empty response", null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                throw ChatCompletionException.upstreamAuthError(ex);
            }
            throw ChatCompletionException.upstreamError(
                    "Upstream request failed with status " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw ChatCompletionException.upstreamTimeout(ex);
        }
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
