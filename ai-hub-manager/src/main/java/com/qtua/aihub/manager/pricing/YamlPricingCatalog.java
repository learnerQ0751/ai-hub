package com.qtua.aihub.manager.pricing;

import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.PricingProperties;
import com.qtua.aihub.common.pricing.ModelPrice;
import com.qtua.aihub.common.pricing.PricingCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * YAML 价格目录实现。
 *
 * <p>启动时把配置映射为不可变目录，并对照模型目录检查每个上游模型是否配置了价格：
 * 缺失时只告警、不阻断启动（参照 LiteLLM 未匹配价格导致 spend=0 的教训）；
 * 非法价格（负数/空）直接启动失败，避免产生错误成本。</p>
 */
@Component
public class YamlPricingCatalog implements PricingCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(YamlPricingCatalog.class);

    private final Map<String, ModelPrice> prices;

    /**
     * 从 YAML 配置构建只读价格目录。
     *
     * @param properties   价格配置
     * @param modelCatalog 模型目录（用于启动时校验价格缺失）
     */
    public YamlPricingCatalog(PricingProperties properties, ModelCatalog modelCatalog) {
        Map<String, ModelPrice> mapped = new LinkedHashMap<>();
        properties.getModels().forEach((model, price) -> mapped.put(model,
                new ModelPrice(price.getInputPerMillion(), price.getOutputPerMillion())));
        this.prices = Collections.unmodifiableMap(mapped);
        warnMissingPrices(modelCatalog);
    }

    /**
     * 查询上游模型单价。
     *
     * @param upstreamModel 上游真实模型名
     * @return 单价；未配置时为空
     */
    @Override
    public Optional<ModelPrice> findPrice(String upstreamModel) {
        if (!StringUtils.hasText(upstreamModel)) {
            return Optional.empty();
        }
        return Optional.ofNullable(prices.get(upstreamModel));
    }

    /**
     * 对照模型目录告警缺失价格的模型。
     *
     * @param modelCatalog 模型目录
     */
    private void warnMissingPrices(ModelCatalog modelCatalog) {
        Set<String> upstreamModels = new TreeSet<>();
        modelCatalog.listAliases().forEach(alias -> modelCatalog.findDeployments(alias).stream()
                .map(ModelDeployment::upstreamModel)
                .forEach(upstreamModels::add));
        upstreamModels.stream()
                .filter(model -> !prices.containsKey(model))
                .forEach(model -> LOG.warn(
                        "No price configured for upstream model '{}'; calls will be recorded with cost=0",
                        model));
    }
}
