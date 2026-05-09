package org.ulpgc.dacd.domain;

import java.math.BigDecimal;

public class ResidentDiscountCalculator {
    private final BigDecimal residentFactor;

    public ResidentDiscountCalculator() {
        this(new BigDecimal("0.25"));
    }

    public ResidentDiscountCalculator(BigDecimal residentFactor) {
        this.residentFactor = residentFactor;
    }

    public BigDecimal getResidentFactor() {
        return residentFactor;
    }

    public BigDecimal estimateResidentPrice(BigDecimal originalPrice) {
        return originalPrice.multiply(residentFactor);
    }

    @Override
    public String toString() {
        return "ResidentDiscountCalculator{" +
                "residentFactor=" + residentFactor +
                '}';
    }
}
