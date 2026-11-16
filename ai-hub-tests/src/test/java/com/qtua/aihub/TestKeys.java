package com.qtua.aihub;

import com.qtua.aihub.api.KeyService;
import com.qtua.aihub.api.model.req.KeyGenerateReqDTO;

import java.util.List;

/**
 * 测试用虚拟 Key 工厂（REQ-5.1）。
 *
 * <p>拆分后数据面不再接受 master key，数据面测试统一通过 KeyService 直接造虚拟 Key。
 * 生成走真实落库路径，鉴权行为与生产一致。</p>
 */
public final class TestKeys {

    private TestKeys() {
    }

    /**
     * 造一个不限制模型、绑指定租户/项目的虚拟 Key。
     *
     * @param keyService Key 服务
     * @param tenantId   租户
     * @param projectId  项目
     * @return 明文 Key
     */
    public static String create(KeyService keyService, String tenantId, String projectId) {
        return create(keyService, tenantId, projectId, List.of());
    }

    /**
     * 造一个限定模型白名单的虚拟 Key。
     *
     * @param keyService Key 服务
     * @param tenantId   租户
     * @param projectId  项目
     * @param models     模型白名单；空表示不限制
     * @return 明文 Key
     */
    public static String create(KeyService keyService, String tenantId, String projectId,
                                List<String> models) {
        return keyService.generate(new KeyGenerateReqDTO(models, "1h", null, null, null,
                null, null, tenantId, projectId)).key();
    }
}
