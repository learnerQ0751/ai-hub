package com.qtua.aihub.console.controller;

import com.qtua.aihub.api.UsageService;
import com.qtua.aihub.api.model.res.UsageResDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成本与用量入口（REQ-4.3）。
 *
 * <p>任意有效 Key 可查：master 可按租户/项目/模型/Key 过滤；虚拟 Key 强制绑定
 * 自身租户、项目与 Key，不能横向查看其他租户。</p>
 */
@RestController
public class UsageController {

    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    private final UsageService usageService;

    /**
     * 构造控制器。
     *
     * @param usageService 用量服务
     */
    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    /**
     * 查询用量聚合与趋势。
     *
     * @param from       起始日期
     * @param to         结束日期
     * @param tenantId   租户（master）
     * @param projectId  项目
     * @param modelAlias 模型别名
     * @param keyId      Key 哈希
     * @return 用量结果
     */
    @GetMapping(path = "/v1/usage", produces = MediaType.APPLICATION_JSON_VALUE)
    public UsageResDTO query(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "tenant_id", required = false) String tenantId,
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "model", required = false) String modelAlias,
            @RequestParam(name = "key_id", required = false) String keyId) {
        return usageService.query(from, to, tenantId, projectId, modelAlias, keyId);
    }

    /**
     * 导出 CSV。
     *
     * @param from       起始日期
     * @param to         结束日期
     * @param tenantId   租户
     * @param projectId  项目
     * @param modelAlias 模型别名
     * @param keyId      Key 哈希
     * @return CSV 文本
     */
    @GetMapping(path = "/v1/usage/export")
    public ResponseEntity<String> export(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "tenant_id", required = false) String tenantId,
            @RequestParam(name = "project_id", required = false) String projectId,
            @RequestParam(name = "model", required = false) String modelAlias,
            @RequestParam(name = "key_id", required = false) String keyId) {
        return ResponseEntity.ok()
                .header("Content-Type", CSV_CONTENT_TYPE)
                .body(usageService.exportCsv(from, to, tenantId, projectId, modelAlias, keyId));
    }
}
