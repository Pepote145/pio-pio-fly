package com.piopiofly.flights;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Descuento de residente canario en vuelos con el resto de España (75%).
 *
 * <p>El descuento real se aplica a la tarifa y no a todas las tasas, así que el resultado es
 * siempre una <strong>estimación</strong> y así debe mostrarse al usuario.
 */
public final class ResidentDiscount {

    public static final BigDecimal RATE = new BigDecimal("0.75");

    private static final BigDecimal RESIDENT_SHARE = BigDecimal.ONE.subtract(RATE);

    private ResidentDiscount() {
    }

    public static BigDecimal estimatePrice(BigDecimal fullPrice) {
        if (fullPrice.signum() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo: " + fullPrice);
        }
        return fullPrice.multiply(RESIDENT_SHARE).setScale(2, RoundingMode.HALF_UP);
    }
}
