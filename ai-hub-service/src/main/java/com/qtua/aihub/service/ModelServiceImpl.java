package com.qtua.aihub.service;

import com.qtua.aihub.api.ModelService;
import com.qtua.aihub.api.model.res.ModelListResDTO;
import com.qtua.aihub.common.catalog.ModelCatalog;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型查询服务实现。
 *
 * <p>只读取模型目录，不接触上游；返回顺序与目录源顺序一致。</p>
 */
@Service
public class ModelServiceImpl implements ModelService {

    private final ModelCatalog modelCatalog;

    /**
     * 构造服务。
     *
     * @param modelCatalog 模型目录
     */
    public ModelServiceImpl(ModelCatalog modelCatalog) {
        this.modelCatalog = modelCatalog;
    }

    /**
     * 返回所有可用模型别名（不暴露部署细节）。
     *
     * @return /models 响应
     */
    @Override
    public ModelListResDTO listModels() {
        return ModelListResDTO.of(List.copyOf(modelCatalog.listAliases()));
    }
}
