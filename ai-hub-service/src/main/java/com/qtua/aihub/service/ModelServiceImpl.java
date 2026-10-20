package com.qtua.aihub.service;

import com.qtua.aihub.api.ModelService;
import com.qtua.aihub.api.model.res.ModelListResDTO;
import com.qtua.aihub.common.config.GatewayProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型查询服务实现。
 *
 * <p>只读取配置，不接触上游；返回顺序与 YAML 配置顺序一致。</p>
 */
@Service
public class ModelServiceImpl implements ModelService {

    private final GatewayProperties properties;

    /**
     * 构造服务。
     *
     * @param properties 网关配置
     */
    public ModelServiceImpl(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * 返回所有可用模型别名（不暴露部署细节）。
     *
     * @return /models 响应
     */
    @Override
    public ModelListResDTO listModels() {
        return ModelListResDTO.of(List.copyOf(properties.getModelList().keySet()));
    }
}
