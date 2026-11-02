package com.qtua.aihub.manager.pricing;

import com.qtua.aihub.common.catalog.ModelCatalog;
import com.qtua.aihub.common.catalog.ModelDeployment;
import com.qtua.aihub.common.config.PricingProperties;
import com.qtua.aihub.common.pricing.ModelPrice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YamlPricingCatalogTest {

    @Test
    void maps_prices_by_upstream_model() {
        YamlPricingCatalog catalog = new YamlPricingCatalog(properties(Map.of(
                "deepseek-chat", price("0.27", "1.10"))), modelCatalog());

        ModelPrice price = catalog.findPrice("deepseek-chat").orElseThrow();

        assertThat(price.inputPerMillion()).isEqualByComparingTo("0.27");
        assertThat(price.outputPerMillion()).isEqualByComparingTo("1.10");
    }

    @Test
    void returns_empty_for_unconfigured_model() {
        YamlPricingCatalog catalog = new YamlPricingCatalog(properties(Map.of()), modelCatalog());

        assertThat(catalog.findPrice("deepseek-chat")).isEmpty();
        assertThat(catalog.findPrice(" ")).isEmpty();
        assertThat(catalog.findPrice(null)).isEmpty();
    }

    @Test
    void missing_price_does_not_block_startup() {
        // 模型目录含 deepseek-chat，但价格表为空：只告警，不抛异常。
        YamlPricingCatalog catalog = new YamlPricingCatalog(properties(Map.of()), modelCatalog());

        assertThat(catalog.findPrice("deepseek-chat")).isEmpty();
    }

    @Test
    void rejects_negative_price() {
        PricingProperties properties = properties(Map.of("deepseek-chat", price("-1", "1.10")));

        assertThatThrownBy(() -> new YamlPricingCatalog(properties, modelCatalog()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PricingProperties properties(Map<String, PricingProperties.Price> prices) {
        PricingProperties properties = new PricingProperties();
        properties.setModels(new java.util.LinkedHashMap<>(prices));
        return properties;
    }

    private PricingProperties.Price price(String input, String output) {
        PricingProperties.Price price = new PricingProperties.Price();
        price.setInputPerMillion(new BigDecimal(input));
        price.setOutputPerMillion(new BigDecimal(output));
        return price;
    }

    private ModelCatalog modelCatalog() {
        ModelCatalog catalog = mock(ModelCatalog.class);
        when(catalog.listAliases()).thenReturn(Set.of("chat"));
        when(catalog.findDeployments("chat")).thenReturn(List.of(
                new ModelDeployment("chat", "https://example.com", "key", "deepseek-chat", 1, 0)));
        return catalog;
    }
}
