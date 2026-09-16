package com.techmatrix18.trading.indicators;

/**
 * Быстрый контейнер для двух линий Стохастика
 *
 * @author Alexander Kuziv
 * @since 16.09.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public record StochasticValue(double k, double d) {
    public static final StochasticValue NEUTRAL = new StochasticValue(50.0, 50.0);
}

