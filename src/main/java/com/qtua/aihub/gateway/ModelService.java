package com.qtua.aihub.gateway;

import com.qtua.aihub.config.GatewayProperties;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelService {

    private final GatewayProperties properties;

    public ModelService(GatewayProperties properties) {
        this.properties = properties;
    }

    public ModelListResponse listModels() {
        return ModelListResponse.of(List.copyOf(properties.getModelList().keySet()));
    }
}
