package com.qtua.aihub.gateway;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping(path = {"/models", "/v1/models"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ModelListResponse listModels() {
        return modelService.listModels();
    }
}
