package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.Indicator;

/**
 * OverIndicatorRule checks if the value of a specified indicator is above a certain threshold.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class OverIndicatorRule implements Rule {
    private final Indicator<Double> indicator;
    private final double threshold;

    public OverIndicatorRule(Indicator<Double> indicator, double threshold) {
        this.indicator = indicator;
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(int i) {
        // Просто берем значение индикатора по индексу i
        // Метод getValue(i) работает мгновенно, так как берет данные из кэша
        return indicator.getValue(i) > threshold;
    }
}

/*
Как использовать:

// --- ВЫШЕ УРОВНЯ (OverIndicatorRule) ---
// RSI находится в зоне перекупленности (выше 70)
Rule rsiIsHigh = new OverIndicatorRule(rsi, 70.0);

// Цена находится выше определенного уровня Фибоначчи
Rule priceAboveFib = new OverIndicatorRule((c) -> fib.calculate(c).get("level_618"), 0.0);

*/

