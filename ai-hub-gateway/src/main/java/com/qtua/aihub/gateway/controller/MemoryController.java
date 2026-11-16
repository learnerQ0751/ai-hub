package com.qtua.aihub.gateway.controller;

import com.qtua.aihub.api.MemoryService;
import com.qtua.aihub.api.model.auth.KeyAuthDTO;
import com.qtua.aihub.api.model.req.MemoryClearReqDTO;
import com.qtua.aihub.api.model.req.MemoryRecallReqDTO;
import com.qtua.aihub.api.model.req.MemorySaveReqDTO;
import com.qtua.aihub.api.model.res.MemoryClearResDTO;
import com.qtua.aihub.api.model.res.MemoryRecallResDTO;
import com.qtua.aihub.api.model.res.MemorySaveResDTO;
import com.qtua.aihub.gateway.filter.AuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 记忆服务入口（数据面，任意有效 Key）。
 *
 * <p>治理由外层 GatewayGovernanceFilter 统一完成：callType=MEMORY，只做 RPM，
 * 调用轨迹写 call_logs；Controller 只负责契约与认证上下文透传。</p>
 */
@RestController
public class MemoryController {

    private final MemoryService memoryService;

    /**
     * 构造控制器。
     *
     * @param memoryService 记忆服务
     */
    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * 保存记忆。
     *
     * @param reqDTO 保存参数
     * @return 201 与记忆 ID
     */
    @PostMapping(path = "/v1/memory/save",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MemorySaveResDTO> save(@RequestBody MemorySaveReqDTO reqDTO) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memoryService.save(reqDTO));
    }

    /**
     * 召回记忆。
     *
     * @param reqDTO 召回参数
     * @return 命中记忆列表
     */
    @PostMapping(path = "/v1/memory/recall",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public MemoryRecallResDTO recall(@RequestBody MemoryRecallReqDTO reqDTO) {
        return memoryService.recall(reqDTO);
    }

    /**
     * 清理记忆并写物理删除审计。
     *
     * @param reqDTO  清理参数
     * @param request HTTP 请求（认证上下文）
     * @return 删除条数与审计 ID
     */
    @PostMapping(path = "/v1/memory/clear",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public MemoryClearResDTO clear(@RequestBody MemoryClearReqDTO reqDTO,
                                   HttpServletRequest request) {
        KeyAuthDTO auth = (KeyAuthDTO) request.getAttribute(AuthFilter.KEY_AUTH_ATTRIBUTE);
        String operatorKey = ObjectUtils.isEmpty(auth) ? null : auth.keyHash();
        return memoryService.clear(reqDTO, operatorKey);
    }
}
