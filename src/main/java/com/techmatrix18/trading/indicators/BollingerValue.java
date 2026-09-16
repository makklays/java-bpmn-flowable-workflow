package com.techmatrix18.trading.indicators;

/**
 * BollingerValue
 * Быстрый контейнер для всех трех лент Боллинджера
 *
 * @author Alexander Kuziv
 * @since 16.09.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public record BollingerValue(double upper, double middle, double lower) {
    public static final BollingerValue EMPTY = new BollingerValue(0.0, 0.0, 0.0);
}

