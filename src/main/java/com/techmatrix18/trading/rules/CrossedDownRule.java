package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.Indicator;

/**
 * CrossedDownRule checks if an indicator has crossed down either another indicator or a constant threshold.
 * Пробой вниз (CrossedDownRule)
 * Противоположность пробою вверх. Используется для сигналов на продажу (Short) или фиксацию прибыли.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class CrossedDownRule implements Rule {
    private final Indicator<Double> indicator;
    private final Indicator<Double> priceIndicator; // Используем для цены
    private final Double constantThreshold;

    // Конструктор для пробития числового уровня (например, RSI < 70)
    public CrossedDownRule(Indicator<Double> indicator, double threshold) {
        this.indicator = indicator;
        this.constantThreshold = threshold;
        this.priceIndicator = null;
    }

    // Конструктор для пробития ценой индикатора (например, Цена < MA)
    public CrossedDownRule(Indicator<Double> indicator, Indicator<Double> priceIndicator) {
        this.indicator = indicator;
        this.priceIndicator = priceIndicator;
        this.constantThreshold = null;
    }

    @Override
    public boolean isSatisfied(int i) {
        if (i < 1) return false;

        double currentVal = indicator.getValue(i);
        double prevVal = indicator.getValue(i - 1);

        if (constantThreshold != null) {
            // Случай 1: Индикатор пересекает число сверху вниз
            return prevVal >= constantThreshold && currentVal < constantThreshold;
        } else if (priceIndicator != null) {
            // Случай 2: Цена пересекает индикатор сверху вниз
            double currentPrice = priceIndicator.getValue(i);
            double prevPrice = priceIndicator.getValue(i - 1);
            return prevPrice >= prevVal && currentPrice < currentVal;
        }

        return false;
    }
}

/*
Как использовать:

// --- РАБОТА С RSI ---
// Индикатор пробил число 70 сверху вниз (Выход из зоны перекупленности)
Rule rsiExitsTop = new CrossedDownRule(rsi, 70.0);

// --- РАБОТА С БОЛЛИНДЖЕРОМ ---
// Цена пробила среднюю линию Боллинджера сверху вниз (Медвежий сигнал)
Rule priceDropsBelowBasis = new CrossedDownRule(bollinger);

// ПРИМЕР "Агрессивный выход":
// Цена упала ниже Боллинджера ИЛИ RSI пробил 70 вниз
Rule aggressiveExit = new CrossedDownRule(bollinger).or(new CrossedDownRule(rsi, 70.0));

*/

