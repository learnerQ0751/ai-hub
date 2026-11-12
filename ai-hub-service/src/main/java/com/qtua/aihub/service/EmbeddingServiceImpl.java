package com.qtua.aihub.service;

import com.qtua.aihub.api.EmbeddingService;
import com.qtua.aihub.api.model.req.EmbeddingReqDTO;
import com.qtua.aihub.api.model.res.EmbeddingDataResDTO;
import com.qtua.aihub.api.model.res.EmbeddingResDTO;
import com.qtua.aihub.api.model.res.EmbeddingUsageResDTO;
import com.qtua.aihub.common.config.RagProperties;
import com.qtua.aihub.common.exception.ChatCompletionException;
import com.qtua.aihub.common.rag.EmbeddingModel;
import com.qtua.aihub.common.rag.EmbeddingModelProvider;
import com.qtua.aihub.common.rag.EmbeddingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 文本向量化服务实现。
 *
 * <p>职责：校验 model/input/dimensions/encoding_format、按别名解析模型、调用
 * {@link EmbeddingModel#embedAllWithUsage}、组装 OpenAI 兼容响应。日志只记录条数、
 * token、维度与耗时，不记录输入正文。</p>
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private static final String PARAM_MODEL = "model";
    private static final String PARAM_INPUT = "input";
    private static final String PARAM_DIMENSIONS = "dimensions";
    private static final String PARAM_ENCODING_FORMAT = "encoding_format";
    private static final String OBJECT_LIST = "list";
    private static final String OBJECT_EMBEDDING = "embedding";
    private static final String FLOAT_FORMAT = "float";

    private final EmbeddingModelProvider modelProvider;
    private final RagProperties properties;

    /**
     * 构造服务。
     *
     * @param modelProvider embedding 模型目录
     * @param properties    RAG 配置（输入条数上限）
     */
    public EmbeddingServiceImpl(EmbeddingModelProvider modelProvider, RagProperties properties) {
        this.modelProvider = modelProvider;
        this.properties = properties;
    }

    /**
     * 执行向量化。
     *
     * @param reqDTO 请求
     * @return OpenAI 兼容响应
     */
    @Override
    public EmbeddingResDTO embed(EmbeddingReqDTO reqDTO) {
        if (Objects.isNull(reqDTO)) {
            throw ChatCompletionException.invalidRequest("请求体不能为空", null);
        }
        String alias = requireModel(reqDTO.getModel());
        List<String> texts = normalizeInput(reqDTO.getInput());
        validateEncodingFormat(reqDTO.getEncodingFormat());
        EmbeddingModel model = modelProvider.findByAlias(alias)
                .orElseThrow(() -> ChatCompletionException.invalidModel(alias));
        Integer dimensions = resolveDimensions(reqDTO.getDimensions(), model);
        long startedAt = System.currentTimeMillis();
        EmbeddingResult result = model.embedAllWithUsage(texts, dimensions);
        int effectiveDimension = Objects.isNull(dimensions) ? model.dimension() : dimensions;
        List<EmbeddingDataResDTO> data = toData(result.vectors(), effectiveDimension);
        LOG.info("embedding completed: alias={}, modelId={}, count={}, dimension={}, inputTokens={}, "
                        + "latencyMs={}",
                alias, model.modelId(), texts.size(), effectiveDimension, result.inputTokens(),
                Math.max(0L, System.currentTimeMillis() - startedAt));
        return new EmbeddingResDTO(OBJECT_LIST, data, alias,
                new EmbeddingUsageResDTO(result.inputTokens(), result.inputTokens()));
    }

    private String requireModel(String model) {
        if (!StringUtils.hasText(model)) {
            throw ChatCompletionException.invalidRequest("model 不能为空", PARAM_MODEL);
        }
        return model.trim();
    }

    private List<String> normalizeInput(Object input) {
        List<String> texts = new ArrayList<>();
        if (input instanceof String text) {
            if (!StringUtils.hasText(text)) {
                throw ChatCompletionException.invalidRequest("input 不能为空", PARAM_INPUT);
            }
            texts.add(text);
        } else if (input instanceof List<?> list) {
            if (list.isEmpty()) {
                throw ChatCompletionException.invalidRequest("input 不能为空", PARAM_INPUT);
            }
            for (Object item : list) {
                if (!(item instanceof String text) || !StringUtils.hasText(text)) {
                    throw ChatCompletionException.invalidRequest(
                            "input 数组只能包含非空字符串", PARAM_INPUT);
                }
                texts.add(text);
            }
        } else {
            throw ChatCompletionException.invalidRequest(
                    "input 必须是字符串或字符串数组", PARAM_INPUT);
        }
        int maxInputs = Math.max(1, properties.getEmbedding().getMaxInputs());
        if (texts.size() > maxInputs) {
            throw ChatCompletionException.invalidRequest(
                    "input 条数不能超过 " + maxInputs, PARAM_INPUT);
        }
        return texts;
    }

    private void validateEncodingFormat(String encodingFormat) {
        if (StringUtils.hasText(encodingFormat) && !FLOAT_FORMAT.equalsIgnoreCase(encodingFormat)) {
            throw ChatCompletionException.invalidRequest(
                    "encoding_format 仅支持 float", PARAM_ENCODING_FORMAT);
        }
    }

    private Integer resolveDimensions(Integer dimensions, EmbeddingModel model) {
        if (ObjectUtils.isEmpty(dimensions)) {
            return null;
        }
        if (dimensions <= 0 || dimensions > model.dimension()) {
            throw ChatCompletionException.invalidRequest(
                    "dimensions 必须在 [1, " + model.dimension() + "] 之间", PARAM_DIMENSIONS);
        }
        return dimensions;
    }

    private List<EmbeddingDataResDTO> toData(List<float[]> vectors, int dimension) {
        if (Objects.isNull(vectors) || vectors.size() == 0) {
            throw ChatCompletionException.upstreamError("embedding 上游返回了空结果", null);
        }
        List<EmbeddingDataResDTO> data = new ArrayList<>(vectors.size());
        for (int index = 0; index < vectors.size(); index++) {
            float[] vector = vectors.get(index);
            if (Objects.isNull(vector) || vector.length != dimension) {
                throw ChatCompletionException.upstreamError(
                        "embedding 上游返回维度与请求不一致", null);
            }
            List<Float> values = new ArrayList<>(vector.length);
            for (float value : vector) {
                values.add(value);
            }
            data.add(new EmbeddingDataResDTO(OBJECT_EMBEDDING, index, values));
        }
        return data;
    }
}
