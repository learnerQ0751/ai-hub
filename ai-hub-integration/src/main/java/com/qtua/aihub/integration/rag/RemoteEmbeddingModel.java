package com.qtua.aihub.integration.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.metering.EmbeddingTokenEstimator;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.common.rag.EmbeddingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OpenAI 兼容远端 embedding 模型（默认阿里 DashScope text-embedding-v3）。
 *
 * <p>按供应商批量上限拆分请求（DashScope v3 官方上限 10 条），失败按配置退避重试；
 * 连接与读取超时显式设置。优先使用供应商返回的 {@code usage.prompt_tokens} 计量，
 * 缺失时回退统一估算。响应逐条按 index 排序并校验维度，异常统一映射为中文业务错误。</p>
 */
public class RemoteEmbeddingModel implements EmbeddingModel {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteEmbeddingModel.class);

    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JSON_MODEL = "model";
    private static final String JSON_INPUT = "input";
    private static final String JSON_DIMENSIONS = "dimensions";
    private static final String JSON_ENCODING_FORMAT = "encoding_format";
    private static final String JSON_DATA = "data";
    private static final String JSON_EMBEDDING = "embedding";
    private static final String JSON_INDEX = "index";
    private static final String JSON_USAGE = "usage";
    private static final String JSON_PROMPT_TOKENS = "prompt_tokens";
    private static final String FLOAT_FORMAT = "float";
    private static final int CLIENT_ERROR_MIN = 400;
    private static final int CLIENT_ERROR_MAX_EXCLUSIVE = 500;
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);
    private static final int COST_SCALE = 8;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String alias;
    private final String modelId;
    private final int dimension;
    private final int maxInputTokens;
    private final int maxBatchSize;
    private final BigDecimal inputPerMillion;
    private final String apiBase;
    private final String apiKey;
    private final String apiKeyEnv;
    private final int maxRetries;
    private final long retryBackoffMs;

    /**
     * 构造远端模型。
     *
     * @param definition   模型配置
     * @param objectMapper JSON 序列化器
     */
    public RemoteEmbeddingModel(EmbeddingModelDefinition definition, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(definition.apiBase())) {
            throw new IllegalStateException("embedding 模型 '" + definition.alias()
                    + "' 缺少 api-base");
        }
        this.objectMapper = objectMapper;
        this.alias = definition.alias();
        this.modelId = definition.model();
        this.dimension = definition.dimension();
        this.maxInputTokens = definition.maxInputTokens();
        this.maxBatchSize = Math.max(1, definition.maxBatchSize());
        this.inputPerMillion = ObjectUtils.isEmpty(definition.inputPerMillion())
                ? BigDecimal.ZERO : definition.inputPerMillion();
        this.apiBase = normalize(definition.apiBase());
        this.apiKey = ObjectUtils.isEmpty(definition.apiKey())
                ? resolveEnv(definition.apiKeyEnv()) : definition.apiKey();
        this.apiKeyEnv = definition.apiKeyEnv();
        this.maxRetries = Math.max(0, definition.maxRetries());
        this.retryBackoffMs = Math.max(0L, definition.retryBackoffMs());
        this.restClient = buildRestClient(definition.timeoutMs());
        LOG.info("remote embedding model configured: alias={}, model={}, dimension={}, "
                        + "maxInputTokens={}, maxBatchSize={}, apiBase={}, apiKeyConfigured={}",
                alias, modelId, dimension, maxInputTokens, maxBatchSize, apiBase,
                StringUtils.hasText(apiKey));
    }

    /**
     * 模型标识。
     *
     * @return 上游真实模型名
     */
    @Override
    public String modelId() {
        return modelId;
    }

    /**
     * 向量维度。
     *
     * @return 维度
     */
    @Override
    public int dimension() {
        return dimension;
    }

    /**
     * 单条最大输入 token。
     *
     * @return 最大输入 token
     */
    @Override
    public int maxInputTokens() {
        return maxInputTokens;
    }

    /**
     * 按输入 token 与配置单价计算成本。
     *
     * @param inputTokens 输入 token
     * @return 成本（8 位小数）
     */
    @Override
    public BigDecimal costOfInputTokens(long inputTokens) {
        if (inputTokens <= 0 || inputPerMillion.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return inputPerMillion.multiply(BigDecimal.valueOf(inputTokens))
                .divide(MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 单条文本向量化。
     *
     * @param text 文本
     * @return 向量
     */
    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    /**
     * 批量文本向量化。
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    @Override
    public List<float[]> embedAll(List<String> texts) {
        return embedAllWithUsage(texts, null).vectors();
    }

    /**
     * 批量向量化并返回供应商 usage（缺失时估算）。
     *
     * @param texts      文本列表
     * @param dimensions 请求维度；null 表示模型默认维度
     * @return 向量与 token 用量
     */
    @Override
    public EmbeddingResult embedAllWithUsage(List<String> texts, Integer dimensions) {
        if (ObjectUtils.isEmpty(texts)) {
            return new EmbeddingResult(List.of(), 0L);
        }
        int effectiveDimensions = resolveDimensions(dimensions);
        validateTexts(texts);
        List<float[]> vectors = new ArrayList<>(texts.size());
        long inputTokens = 0L;
        for (int start = 0; start < texts.size(); start += maxBatchSize) {
            int end = Math.min(start + maxBatchSize, texts.size());
            List<String> batch = texts.subList(start, end);
            BatchResult result = callBatchWithRetry(batch, effectiveDimensions);
            vectors.addAll(result.vectors());
            inputTokens += result.inputTokens();
        }
        return new EmbeddingResult(vectors, inputTokens);
    }

    /**
     * 重写维度校验：远端模型允许 ≤ 配置维度的子维度（供应商支持时）；未知值由上游拒绝。
     *
     * @param dimensions 请求维度
     */
    @Override
    public void validateDimensions(Integer dimensions) {
        resolveDimensions(dimensions);
    }

    private int resolveDimensions(Integer dimensions) {
        if (ObjectUtils.isEmpty(dimensions)) {
            return dimension;
        }
        if (dimensions <= 0 || dimensions > dimension) {
            throw ChatCompletionException.invalidRequest(
                    "dimensions 必须在 [1, " + dimension + "] 之间", "dimensions");
        }
        return dimensions;
    }

    private void validateTexts(List<String> texts) {
        for (String text : texts) {
            if (!StringUtils.hasText(text)) {
                throw ChatCompletionException.invalidRequest("input 文本不能为空", "input");
            }
            long tokens = EmbeddingTokenEstimator.estimate(text);
            if (tokens > maxInputTokens) {
                throw ChatCompletionException.invalidRequest(
                        "单条文本超过模型最大输入 " + maxInputTokens + " tokens", "input");
            }
        }
    }

    private BatchResult callBatchWithRetry(List<String> batch, int dimensions) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return callBatch(batch, dimensions);
            } catch (ChatCompletionException ex) {
                last = ex;
                if (!ex.isRetryable() || attempt == maxRetries) {
                    throw ex;
                }
                LOG.warn("remote embedding batch failed, retrying: alias={}, attempt={}, "
                        + "size={}, type={}", alias, attempt + 1, batch.size(), ex.getType());
                sleepBackoff(attempt);
            }
        }
        throw last;
    }

    private BatchResult callBatch(List<String> batch, int dimensions) {
        ensureConfigured();
        String body = buildRequest(batch, dimensions);
        String response;
        try {
            response = restClient.post()
                    .uri(apiBase + EMBEDDINGS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            throw mapResponseError(ex);
        } catch (ResourceAccessException ex) {
            throw ChatCompletionException.upstreamTimeout(ex);
        }
        if (!StringUtils.hasText(response)) {
            throw ChatCompletionException.upstreamInvalidResponse("embedding 上游返回了空响应");
        }
        return parseResponse(response, batch, dimensions);
    }

    private String buildRequest(List<String> batch, int dimensions) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put(JSON_MODEL, modelId);
        ArrayNode input = root.putArray(JSON_INPUT);
        batch.forEach(input::add);
        root.put(JSON_ENCODING_FORMAT, FLOAT_FORMAT);
        if (dimensions != dimension) {
            root.put(JSON_DIMENSIONS, dimensions);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.internalError("embedding 请求序列化失败", ex);
        }
    }

    private BatchResult parseResponse(String response, List<String> batch, int dimensions) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path(JSON_DATA);
            int expectedSize = batch.size();
            if (!data.isArray() || data.size() != expectedSize) {
                throw ChatCompletionException.upstreamInvalidResponse(
                        "embedding 上游返回条数与请求不一致");
            }
            List<JsonNode> ordered = new ArrayList<>(expectedSize);
            data.forEach(ordered::add);
            ordered.sort(Comparator.comparingInt(node -> node.path(JSON_INDEX).asInt(0)));
            List<float[]> vectors = new ArrayList<>(expectedSize);
            for (JsonNode item : ordered) {
                vectors.add(parseVector(item.path(JSON_EMBEDDING), dimensions));
            }
            long tokens = parseInputTokens(root.path(JSON_USAGE), batch);
            return new BatchResult(vectors, tokens);
        } catch (JsonProcessingException ex) {
            throw ChatCompletionException.upstreamInvalidResponse(
                    "embedding 上游响应不是合法 JSON");
        }
    }

    private float[] parseVector(JsonNode embedding, int dimensions) {
        if (!embedding.isArray() || embedding.size() != dimensions) {
            throw ChatCompletionException.upstreamInvalidResponse(
                    "embedding 上游返回维度与请求不一致");
        }
        float[] vector = new float[dimensions];
        for (int index = 0; index < dimensions; index++) {
            JsonNode value = embedding.get(index);
            if (!value.isNumber()) {
                throw ChatCompletionException.upstreamInvalidResponse(
                        "embedding 上游返回了非法向量值");
            }
            vector[index] = (float) value.asDouble();
        }
        return vector;
    }

    private long parseInputTokens(JsonNode usage, List<String> batch) {
        if (usage.isObject() && usage.path(JSON_PROMPT_TOKENS).canConvertToLong()) {
            long tokens = usage.path(JSON_PROMPT_TOKENS).asLong();
            if (tokens > 0) {
                return tokens;
            }
        }
        // 供应商未返回 usage：按统一规则估算，保证计量不为 0。
        return Math.max(1L, EmbeddingTokenEstimator.estimateAll(batch));
    }

    private ChatCompletionException mapResponseError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return ChatCompletionException.upstreamAuthError(ex);
        }
        if (status == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return ChatCompletionException.upstreamRateLimited(ex);
        }
        if (status >= CLIENT_ERROR_MIN && status < CLIENT_ERROR_MAX_EXCLUSIVE) {
            return ChatCompletionException.upstreamClientError(status, ex);
        }
        return ChatCompletionException.upstreamError(
                "embedding 上游请求失败，状态码 " + status, ex);
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(apiKey)) {
            String envHint = StringUtils.hasText(apiKeyEnv) ? apiKeyEnv : "DASHSCOPE_API_KEY";
            throw ChatCompletionException.embeddingNotConfigured(
                    "模型 '" + alias + "' 缺少 API Key，请设置环境变量 " + envHint);
        }
    }

    private void sleepBackoff(int attempt) {
        long backoff = retryBackoffMs * (attempt + 1L);
        if (backoff <= 0L) {
            return;
        }
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("embedding retry interrupted", ex);
        }
    }

    private static RestClient buildRestClient(int timeoutMs) {
        int timeout = Math.max(1, timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeout));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static String resolveEnv(String apiKeyEnv) {
        return StringUtils.hasText(apiKeyEnv) ? System.getenv(apiKeyEnv) : null;
    }

    private String normalize(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    /**
     * 一批调用的结果。
     *
     * @param vectors     向量
     * @param inputTokens 输入 token
     */
    private record BatchResult(List<float[]> vectors, long inputTokens) {
    }
}
