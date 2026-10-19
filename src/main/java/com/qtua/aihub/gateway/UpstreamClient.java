package com.qtua.aihub.gateway;

import com.qtua.aihub.config.GatewayProperties.Deployment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class UpstreamClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String BEARER_PREFIX = "Bearer ";

    private final RestClient restClient;

    public UpstreamClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public String chat(Deployment deployment, String requestBody) {
        try {
            String response = restClient.post()
                    .uri(normalize(deployment.getApiBase()) + CHAT_COMPLETIONS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + deployment.getApiKey())
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

    private String normalize(String apiBase) {
        if (apiBase.endsWith("/")) {
            return apiBase.substring(0, apiBase.length() - 1);
        }
        return apiBase;
    }
}
