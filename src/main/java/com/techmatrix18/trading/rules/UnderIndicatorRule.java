package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.Indicator;

/**
 * UnderIndicatorRule checks if the value of a specified indicator is below a certain threshold.
 * Ниже уровня (UnderIndicatorRule)
 * Проверка на то, что цена или индикатор находится в определенной зоне.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class UnderIndicatorRule implements Rule {
    private final Indicator<Double> indicator;
    private final double threshold;

    public UnderIndicatorRule(Indicator<Double> indicator, double threshold) {
        this.indicator = indicator;
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(int i) {
        // Получаем значение из заранее рассчитанной истории индикатора
        // Это работает за O(1), не пересчитывая всю историю
        return indicator.getValue(i) < threshold;
    }
}

/*
Как использовать:

// --- НИЖЕ УРОВНЯ (UnderIndicatorRule) ---
// RSI находится в зоне перепроданности (ниже 30)
Rule rsiIsLow = new UnderIndicatorRule(rsi, 30.0);

// Цена находится ниже средней линии Боллинджера
Rule priceUnderBB = new UnderIndicatorRule(bollinger, 0.0);

*/

