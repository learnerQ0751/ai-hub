package com.qtua.aihub.api;

import com.qtua.aihub.api.model.res.UsageResDTO;

/**
 * 成本与用量统计（REQ-4.3）。
 */
public interface UsageService {

    /**
     * 查询用量聚合。
     *
     * @param from       起始日期（yyyy-MM-dd，可空 = 近 7 天）
     * @param to         结束日期（可空 = 今天）
     * @param tenantId   租户（可空；虚拟 Key 强制绑定租户）
     * @param projectId  项目（可空）
     * @param modelAlias 模型别名（可空）
     * @param keyId      Key 哈希（可空；虚拟 Key 强制自己）
     * @return 用量结果
     */
    UsageResDTO query(String from, String to, String tenantId, String projectId,
                      String modelAlias, String keyId);

    /**
     * 导出 CSV（与 query 同参数、同口径）。
     *
     * @param from       起始日期
     * @param to         结束日期
     * @param tenantId   租户
     * @param projectId  项目
     * @param modelAlias 模型别名
     * @param keyId      Key 哈希
     * @return CSV 文本
     */
    String exportCsv(String from, String to, String tenantId, String projectId,
                     String modelAlias, String keyId);
}
