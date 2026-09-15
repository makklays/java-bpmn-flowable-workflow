package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.Indicator;

/**
 * IndicatorCrossedUpRule checks if one indicator has crossed above another indicator.
 * Правило только для пересечения скользящих средних (CrossedUpRule)
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class IndicatorCrossedUpRule implements Rule {
    private final Indicator<Double> fast;
    private final Indicator<Double> slow;

    public IndicatorCrossedUpRule(Indicator<Double> fast, Indicator<Double> slow) {
        this.fast = fast;
        this.slow = slow;
    }

    @Override
    public boolean isSatisfied(int i) {
        // Для пересечения нужно минимум 2 свечи (текущая и предыдущая)
        if (i < 1) return false;

        // Просто берем значения по индексам из индикаторов
        double currentFast = fast.getValue(i);
        double currentSlow = slow.getValue(i);

        double prevFast = fast.getValue(i - 1);
        double prevSlow = slow.getValue(i - 1);

        // Логика пересечения: была снизу (или на уровне), стала сверху
        return prevFast <= prevSlow && currentFast > currentSlow;
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

