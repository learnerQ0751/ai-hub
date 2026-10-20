package com.qtua.aihub.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ModelListResponse(String object, List<ModelInfo> data) {

    private static final String LIST_OBJECT = "list";

    public ModelListResponse {
        data = List.copyOf(data);
    }
    private static final String MODEL_OBJECT = "model";
    private static final String OWNER = "ai-hub";
    private static final long NO_CREATED_TIME = 0;

    public static ModelListResponse of(List<String> aliases) {
        List<ModelInfo> models = aliases.stream()
                .map(alias -> new ModelInfo(alias, MODEL_OBJECT, NO_CREATED_TIME, OWNER))
                .toList();
        return new ModelListResponse(LIST_OBJECT, models);
    }

    public record ModelInfo(String id, String object, long created,
                            @JsonProperty("owned_by") String ownedBy) {
    }
}
