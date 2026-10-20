package com.qtua.aihub.api;

import com.qtua.aihub.api.model.res.ModelListResDTO;

/**
 * 模型查询应用服务契约。
 */
public interface ModelService {

    /**
     * 返回可用模型别名列表。
     *
     * @return /models 响应
     */
    ModelListResDTO listModels();
}
