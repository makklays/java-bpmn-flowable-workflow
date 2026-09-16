package com.techmatrix18.trading.indicators;

/**
 * Быстрый контейнер данных (Использует double, не использую Double)
 *
 * @author Alexander Kuziv
 * @since 16.09.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public record MacdValue(double macdLine, double signalLine, double histogram) {
    public static final MacdValue EMPTY = new MacdValue(0.0, 0.0, 0.0);
}

