package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.Indicator;

/**
 * CrossedUpRule checks if the closing price of the current candle has crossed above a specified indicator level.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class CrossedUpRule implements Rule {
    private final Indicator<Double> indicator;
    private final Indicator<Double> priceIndicator;
    private final Double constantThreshold;

    // Конструктор 1: Индикатор пересекает числовое значение (например, RSI выходит из 30 вверх)
    public CrossedUpRule(Indicator<Double> indicator, double threshold) {
        this.indicator = indicator;
        this.constantThreshold = threshold;
        this.priceIndicator = null;
    }

    // Конструктор 2: Цена пересекает индикатор (например, Цена пробивает MA снизу вверх)
    public CrossedUpRule(Indicator<Double> indicator, Indicator<Double> priceIndicator) {
        this.indicator = indicator;
        this.priceIndicator = priceIndicator;
        this.constantThreshold = null;
    }

    @Override
    public boolean isSatisfied(int i) {
        // Пересечение требует как минимум две точки (текущую и предыдущую)
        if (i < 1) return false;

        double currentVal = indicator.getValue(i);
        double prevVal = indicator.getValue(i - 1);

        if (constantThreshold != null) {
            // Случай 1: Линия индикатора пересекает порог снизу вверх
            return prevVal <= constantThreshold && currentVal > constantThreshold;
        } else if (priceIndicator != null) {
            // Случай 2: Цена пересекает линию индикатора снизу вверх
            double currentPrice = priceIndicator.getValue(i);
            double prevPrice = priceIndicator.getValue(i - 1);
            return prevPrice <= prevVal && currentPrice > currentVal;
        }

        return false;
    }
}

/*
Как использовать:

// --- ПЕРЕСЕЧЕНИЕ ВВЕРХ (CrossedUpRule) ---
Rule buyRule = new CrossedUpRule(bollinger).and(new UnderIndicatorRule(rsi, 40.0));

// Цена пересекает индикатор вверх (например, пробой средней линии Боллинджера)
Rule buyPriceCrossBB = new CrossedUpRule(bollinger);

// Сам индикатор пересекает уровень (например, RSI выходит из зоны перепроданности 30)
Rule rsiLeavesBottom = new CrossedUpRule(rsi, 30.0);

// ПРИМЕР "Консервативный вход":
// Цена пробила Боллинджер вверх И при этом RSI все еще низкий (не перекуплен)
Rule conservativeBuy = new CrossedUpRule(bollinger).and(new UnderIndicatorRule(rsi, 50.0));

*/

