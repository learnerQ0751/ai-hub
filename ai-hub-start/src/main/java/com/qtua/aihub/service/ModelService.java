package com.qtua.aihub.service;

import com.qtua.aihub.config.GatewayProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型别名查询服务。
 *
 * <p>只读取配置，不接触上游；返回顺序与 YAML 配置顺序一致。</p>
 */
@Service
public class ModelService {

    private final GatewayProperties properties;

    /**
     * 构造服务。
     *
     * @param properties 网关配置
     */
    public ModelService(GatewayProperties properties) {
        this.properties = properties;
    }

    /**
     * 返回所有可用模型别名（不暴露部署细节）。
     *
     * @return 模型别名列表
     */
    public List<String> listModels() {
        return List.copyOf(properties.getModelList().keySet());
    }
}
