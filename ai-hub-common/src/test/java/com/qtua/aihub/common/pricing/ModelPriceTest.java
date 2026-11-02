package com.qtua.aihub.common.pricing;

import com.qtua.aihub.common.metering.TokenUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelPriceTest {

    @Test
    void prices_one_million_input_tokens_at_input_unit_price() {
        ModelPrice price = new ModelPrice(new BigDecimal("0.27"), new BigDecimal("1.10"));

        BigDecimal cost = price.costOf(new TokenUsage(1_000_000, 0, 1_000_000));

        assertThat(cost).isEqualByComparingTo("0.27");
    }

    @Test
    void combines_input_and_output_prices() {
        ModelPrice price = new ModelPrice(new BigDecimal("0.27"), new BigDecimal("1.10"));

        BigDecimal cost = price.costOf(new TokenUsage(1_000_000, 500_000, 1_500_000));

        assertThat(cost).isEqualByComparingTo("0.82");
    }

    @Test
    void keeps_eight_decimal_places_for_small_calls() {
        ModelPrice price = new ModelPrice(new BigDecimal("1.00"), new BigDecimal("2.00"));

        BigDecimal cost = price.costOf(new TokenUsage(1, 1, 2));

        assertThat(cost.scale()).isEqualTo(8);
        assertThat(cost).isEqualByComparingTo("0.00000300");
    }

    @Test
    void rejects_negative_tokens() {
        ModelPrice price = new ModelPrice(BigDecimal.ONE, BigDecimal.ONE);

        assertThatThrownBy(() -> price.costOf(new TokenUsage(-1, 0, -1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_negative_unit_prices() {
        assertThatThrownBy(() -> new ModelPrice(new BigDecimal("-0.1"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
