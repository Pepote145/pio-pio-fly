package com.piopiofly.flights;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResidentDiscountTest {

    @Test
    void residentPaysAQuarterOfThePrice() {
        assertThat(ResidentDiscount.estimatePrice(new BigDecimal("120.00"))).isEqualByComparingTo("30.00");
    }

    @Test
    void roundsToCents() {
        assertThat(ResidentDiscount.estimatePrice(new BigDecimal("99.99"))).isEqualByComparingTo("25.00");
    }

    @Test
    void rejectsNegativePrices() {
        assertThatThrownBy(() -> ResidentDiscount.estimatePrice(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
