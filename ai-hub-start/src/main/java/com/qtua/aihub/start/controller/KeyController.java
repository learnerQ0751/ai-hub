package com.qtua.aihub.start.controller;

import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.api.model.req.KeyBlockReqDTO;
import com.qtua.aihub.api.model.req.KeyGenerateReqDTO;
import com.qtua.aihub.api.model.res.KeyBlockResDTO;
import com.qtua.aihub.api.model.res.KeyGenerateResDTO;
import com.qtua.aihub.api.model.res.KeyInfoResDTO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 虚拟 Key 管理入口。
 *
 * <p>所有 /key/* 接口仅 master key 可访问（由 AuthFilter 在进入 Controller 前拦截）。
 * 生成接口返回一次性明文，其余接口只返回掩码。</p>
 */
@RestController
public class KeyController {

    private final KeyService keyService;

    /**
     * 构造控制器。
     *
     * @param keyService 虚拟 Key 服务
     */
    public KeyController(KeyService keyService) {
        this.keyService = keyService;
    }

    /**
     * 生成虚拟 Key。
     *
     * @param reqDTO 生成参数
     * @return 明文 Key 与元信息
     */
    @PostMapping(path = "/key/generate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public KeyGenerateResDTO generate(@RequestBody KeyGenerateReqDTO reqDTO) {
        return keyService.generate(reqDTO);
    }

    /**
     * 查询虚拟 Key 信息。
     *
     * @param key 明文 Key（查询参数）
     * @return 元信息（Key 已掩码）
     */
    @GetMapping(path = "/key/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public KeyInfoResDTO info(@RequestParam(name = "key", required = false) String key) {
        return keyService.info(key);
    }

    /**
     * 封禁虚拟 Key。
     *
     * @param reqDTO 含明文 Key 的请求
     * @return 封禁结果
     */
    @PostMapping(path = "/key/block",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public KeyBlockResDTO block(@RequestBody KeyBlockReqDTO reqDTO) {
        return keyService.block(reqDTO.key());
    }

    /**
     * 解封虚拟 Key。
     *
     * @param reqDTO 含明文 Key 的请求
     * @return 解封结果
     */
    @PostMapping(path = "/key/unblock",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public KeyBlockResDTO unblock(@RequestBody KeyBlockReqDTO reqDTO) {
        return keyService.unblock(reqDTO.key());
    }
}
