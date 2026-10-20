package com.qtua.aihub.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OpenAI /models 兼容响应。
 *
 * @param object 固定为 "list"
 * @param data   模型别名列表
 */
public record ModelListResponse(String object, List<ModelInfo> data) {

    private static final String LIST_OBJECT = "list";
    private static final String MODEL_OBJECT = "model";
    private static final String OWNER = "ai-hub";
    private static final long NO_CREATED_TIME = 0;

    /**
     * 防御性复制，避免调用方修改内部列表。
     */
    public ModelListResponse {
        data = List.copyOf(data);
    }

    /**
     * 从别名列表构造响应。
     *
     * @param aliases 模型别名
     * @return /models 响应
     */
    public static ModelListResponse of(List<String> aliases) {
        List<ModelInfo> models = aliases.stream()
                .map(alias -> new ModelInfo(alias, MODEL_OBJECT, NO_CREATED_TIME, OWNER))
                .toList();
        return new ModelListResponse(LIST_OBJECT, models);
    }

    /**
     * 单个模型条目。
     *
     * @param id      模型别名
     * @param object  固定为 "model"
     * @param created 创建时间（兼容字段，固定 0）
     * @param ownedBy 所有者
     */
    public record ModelInfo(String id, String object, long created,
                            @JsonProperty("owned_by") String ownedBy) {
    }
}
