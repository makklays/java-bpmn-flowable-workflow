package com.techmatrix18.trading.indicators;

/**
 * FibLevels
 * Быстрый и легкий контейнер уровней Фибоначчи
 *
 * @author Alexander Kuziv
 * @since 16.09.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public record FibLevels(
    double lvl0,
    double lvl236,
    double lvl382,
    double lvl500,
    double lvl618,
    double lvl786,
    double lvl100
) {
    public static final FibLevels EMPTY = new FibLevels(0, 0, 0, 0, 0, 0, 0);
}

