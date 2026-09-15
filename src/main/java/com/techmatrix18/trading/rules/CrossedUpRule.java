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
    private final Indicator<Double> first;
    private final Indicator<Double> second;
    private final Double constantThreshold;

    // Конструктор 1: Индикатор пересекает фиксированный порог снизу вверх (например, RSI выходит из 30)
    public CrossedUpRule(Indicator<Double> indicator, double threshold) {
        this.first = indicator;
        this.constantThreshold = threshold;
        this.second = null;
    }

    // Конструктор 2: first пересекает second снизу вверх (например, Цена пробивает MA, или Быстрая MA пробивает Медленную)
    public CrossedUpRule(Indicator<Double> first, Indicator<Double> second) {
        this.first = first;
        this.second = second;
        this.constantThreshold = null;
    }

    @Override
    public boolean isSatisfied(int i) {
        if (i < 1) return false;

        double currentFirst = first.getValue(i);
        double prevFirst = first.getValue(i - 1);

        if (constantThreshold != null) {
            return prevFirst <= constantThreshold && currentFirst > constantThreshold;
        } else if (second != null) {
            double currentSecond = second.getValue(i);
            double prevSecond = second.getValue(i - 1);

            // Четкая логика: первый был ниже/равен второму, а стал строго выше
            return prevFirst <= prevSecond && currentFirst > currentSecond;
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

