package com.qtua.aihub.gateway.controller;

import com.qtua.aihub.api.ModelService;
import com.qtua.aihub.api.model.res.ModelListResDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型别名查询入口。
 *
 * <p>只暴露别名，不暴露上游模型名、api-base、api-key。</p>
 */
@RestController
public class ModelController {

    private final ModelService modelService;

    /**
     * 构造控制器。
     *
     * @param modelService 模型查询服务
     */
    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    /**
     * 返回配置中的可用模型别名。
     *
     * @return OpenAI /models 兼容结构
     */
    @GetMapping(path = {"/models", "/v1/models"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ModelListResDTO listModels() {
        return modelService.listModels();
    }
}
