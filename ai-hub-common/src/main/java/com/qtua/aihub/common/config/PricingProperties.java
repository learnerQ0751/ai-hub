package com.qtua.aihub.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型价格配置。
 *
 * <p>按上游真实模型名配置每 1M tokens 的输入/输出单价，例如：
 * <pre>{@code
 * gateway:
 *   pricing:
 *     models:
 *       deepseek-chat:
 *         input-per-million: 0.27
 *         output-per-million: 1.10
 * }</pre>
 * 价格缺失不阻断启动，由 {@code YamlPricingCatalog} 告警并按 0 成本处理。</p>
 */
@ConfigurationProperties(prefix = "gateway.pricing")
public class PricingProperties {

    /** 上游模型名 → 单价。 */
    private Map<String, Price> models = new LinkedHashMap<>();

    public Map<String, Price> getModels() {
        return models;
    }

    public void setModels(Map<String, Price> models) {
        this.models = models;
    }

    /**
     * 单个模型的单价。
     */
    public static class Price {

        /** 输入单价（每 1M tokens）。 */
        private BigDecimal inputPerMillion;
        /** 输出单价（每 1M tokens）。 */
        private BigDecimal outputPerMillion;

        public BigDecimal getInputPerMillion() {
            return inputPerMillion;
        }

        public void setInputPerMillion(BigDecimal inputPerMillion) {
            this.inputPerMillion = inputPerMillion;
        }

        public BigDecimal getOutputPerMillion() {
            return outputPerMillion;
        }

        public void setOutputPerMillion(BigDecimal outputPerMillion) {
            this.outputPerMillion = outputPerMillion;
        }
    }
}
