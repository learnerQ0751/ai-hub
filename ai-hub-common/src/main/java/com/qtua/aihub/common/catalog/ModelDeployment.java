package com.qtua.aihub.common.catalog;

/**
 * 模型部署领域模型。
 *
 * <p>与配置类解耦：未来模型目录来自数据库时，业务代码无需改动。
 * apiKey 属于敏感字段，toString 必须脱敏，禁止输出到日志。</p>
 *
 * @param alias         模型别名
 * @param apiBase       上游 OpenAI 兼容地址
 * @param apiKey        上游 API Key（敏感）
 * @param upstreamModel 上游真实模型名
 * @param weight        加权随机权重
 * @param priority      主备优先级：数值越小越优先；同优先级内按权重随机
 */
public record ModelDeployment(
        String alias,
        String apiBase,
        String apiKey,
        String upstreamModel,
        int weight,
        int priority) {

    /**
     * 脱敏输出：不包含 apiKey。
     *
     * @return 可安全打印的描述
     */
    @Override
    public String toString() {
        return "ModelDeployment[alias=" + alias
                + ", upstreamModel=" + upstreamModel
                + ", apiBase=" + apiBase
                + ", weight=" + weight
                + ", priority=" + priority + "]";
    }
}
