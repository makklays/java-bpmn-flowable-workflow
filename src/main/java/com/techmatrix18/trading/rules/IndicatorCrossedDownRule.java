package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.Indicator;

/**
 * IndicatorCrossedDownRule checks if one indicator has crossed below another indicator.
 * Правило только для пересечения скользящих средних (CrossedDownRule)
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class IndicatorCrossedDownRule implements Rule {
    private final Indicator<Double> fast;
    private final Indicator<Double> slow;

    public IndicatorCrossedDownRule(Indicator<Double> fast, Indicator<Double> slow) {
        this.fast = fast;
        this.slow = slow;
    }

    @Override
    public boolean isSatisfied(int i) {
        // Пересечение требует как минимум две точки
        if (i < 1) return false;

        // Берем готовые значения из кэша индикаторов по индексам
        double currentFast = fast.getValue(i);
        double currentSlow = slow.getValue(i);

        double prevFast = fast.getValue(i - 1);
        double prevSlow = slow.getValue(i - 1);

        // Условие: раньше быстрая была выше (или равна), а теперь стала ниже
        return prevFast >= prevSlow && currentFast < currentSlow;
    }
}

/*
Как использовать:

// Инициализируем индикаторы
EmaIndicator ema9 = new EmaIndicator(9);
EmaIndicator ema21 = new EmaIndicator(21);

// 1. Золотой крест (EMA 9 пересекает EMA 21 вверх)
Rule goldenCross = new IndicatorCrossedUpRule(ema9, ema21);

// 2. Смертельный крест (EMA 9 пересекает EMA 21 вниз)
Rule deathCross = new IndicatorCrossedDownRule(ema9, ema21);

if (goldenCross.isSatisfied(candles)) {
    telegramService.sendMessageForAll("🚀 Golden Cross! Быстрая EMA 9 пробила медленную EMA 21 вверх.");
}

*/

