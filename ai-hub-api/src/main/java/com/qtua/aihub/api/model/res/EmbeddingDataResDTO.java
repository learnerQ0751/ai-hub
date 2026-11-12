package com.qtua.aihub.api.model.res;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * 单条 embedding 结果。
 *
 * @param object    固定 "embedding"
 * @param index     与请求输入的顺序一致
 * @param embedding 向量
 */
public record EmbeddingDataResDTO(
        @JsonProperty("object") String object,
        @JsonProperty("index") int index,
        @JsonProperty("embedding") List<Float> embedding) {

    /**
     * 防御性复制向量。
     */
    public EmbeddingDataResDTO {
        embedding = Objects.isNull(embedding) ? List.of() : List.copyOf(embedding);
    }
}
