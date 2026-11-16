package com.qtua.aihub.console.controller;

import com.qtua.aihub.api.PromptService;
import com.qtua.aihub.api.model.req.PromptCreateReqDTO;
import com.qtua.aihub.api.model.req.PromptPublishReqDTO;
import com.qtua.aihub.api.model.req.PromptVersionCreateReqDTO;
import com.qtua.aihub.api.model.res.PromptListResDTO;
import com.qtua.aihub.api.model.res.PromptResDTO;
import com.qtua.aihub.api.model.res.PromptVersionListResDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prompt 模板管理入口（仅 master key；render/chat 数据面在 REQ-2.5 提供）。
 */
@RestController
public class PromptController {

    private final PromptService promptService;

    /**
     * 构造控制器。
     *
     * @param promptService Prompt 服务
     */
    public PromptController(PromptService promptService) {
        this.promptService = promptService;
    }

    /**
     * 创建模板 v1。
     *
     * @param reqDTO 创建参数
     * @return 201 与创建的版本
     */
    @PostMapping(path = "/v1/prompts",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PromptResDTO> create(@RequestBody PromptCreateReqDTO reqDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promptService.create(reqDTO));
    }

    /**
     * 追加新版本。
     *
     * @param name   模板名
     * @param reqDTO 版本参数
     * @return 201 与新版本
     */
    @PostMapping(path = "/v1/prompts/{name}/versions",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PromptResDTO> createVersion(@PathVariable("name") String name,
                                                      @RequestBody PromptVersionCreateReqDTO reqDTO) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(promptService.createVersion(name, reqDTO));
    }

    /**
     * 模板列表（每个模板的最新发布版本）。
     *
     * @param tenantId 租户过滤（可选）
     * @return 模板列表
     */
    @GetMapping(path = "/v1/prompts", produces = MediaType.APPLICATION_JSON_VALUE)
    public PromptListResDTO list(
            @RequestParam(name = "tenant_id", required = false) String tenantId) {
        return promptService.list(tenantId);
    }

    /**
     * 查询模板（默认最新发布版）。
     *
     * @param name    模板名
     * @param version 指定版本（可选）
     * @return 版本视图
     */
    @GetMapping(path = "/v1/prompts/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public PromptResDTO get(@PathVariable("name") String name,
                            @RequestParam(name = "version", required = false) Integer version) {
        return promptService.get(name, version);
    }

    /**
     * 版本历史。
     *
     * @param name 模板名
     * @return 版本列表
     */
    @GetMapping(path = "/v1/prompts/{name}/versions",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PromptVersionListResDTO versions(@PathVariable("name") String name) {
        return promptService.versions(name);
    }

    /**
     * 发布指定版本（发布旧版本即回滚）。
     *
     * @param name   模板名
     * @param reqDTO 发布参数
     * @return 发布后的版本
     */
    @PostMapping(path = "/v1/prompts/{name}/publish",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PromptResDTO publish(@PathVariable("name") String name,
                                @RequestBody PromptPublishReqDTO reqDTO) {
        return promptService.publish(name, reqDTO.version());
    }
}
