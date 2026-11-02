package com.qtua.aihub.common.catalog;

/**
 * 模型部署领域模型。
 *
 * <p>与配置类解耦：未来模型目录来自数据库时，业务代码无需改动。
 * apiKey 属于敏感字段，toString 必须脱敏，禁止输出到日志。</p>
 */
public record ModelDeployment(
        String alias,
        String apiBase,
        String apiKey,
        String upstreamModel,
        int weight) {

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
                + ", weight=" + weight + "]";
    }
}
