package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * OpenAI 兼容 embedding 响应。
 *
 * @param object 固定 "list"
 * @param data   向量列表
 * @param model  请求的模型别名
 * @param usage  Token 用量
 */
public record EmbeddingResDTO(
        @JsonProperty("object") String object,
        @JsonProperty("data") List<EmbeddingDataResDTO> data,
        @JsonProperty("model") String model,
        @JsonProperty("usage") EmbeddingUsageResDTO usage) {

    /**
     * 防御性复制数据列表。
     */
    public EmbeddingResDTO {
        data = Objects.isNull(data) ? List.of() : List.copyOf(data);
    }
}
