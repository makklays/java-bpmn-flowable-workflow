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
    private final Indicator<Double> first;
    private final Indicator<Double> second;
    private final Double constantThreshold;

    // Конструктор 1: Индикатор пересекает числовое значение сверху вниз (например, RSI падает ниже 70)
    public CrossedDownRule(Indicator<Double> indicator, double threshold) {
        this.first = indicator;
        this.constantThreshold = threshold;
        this.second = null;
    }

    // Конструктор 2: first пересекает second сверху вниз
    // Например: Цена пробивает MA вниз -> first = Цена, second = MA
    // Например: Быстрая MA пробивает Медленную вниз -> first = Быстрая MA, second = Медленная MA
    public CrossedDownRule(Indicator<Double> first, Indicator<Double> second) {
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
            // Первый индикатор пересекает фиксированный порог сверху вниз
            return prevFirst >= constantThreshold && currentFirst < constantThreshold;
        } else if (second != null) {
            double currentSecond = second.getValue(i);
            double prevSecond = second.getValue(i - 1);

            // Четкая и универсальная логика:
            // Первый был ВЫШЕ или равен Второму, а стал строго НИЖЕ
            return prevFirst >= prevSecond && currentFirst < currentSecond;
        }

        return false;
    }
}

/*
Как использовать:

// --- РАБОТА С RSI ---
// Индикатор пробил число 70 сверху вниз (Выход из зоны перекупленности)
Rule rsiCrossDown = new CrossedDownRule(rsiIndicator, 70.0);

// Цена пробивает MA сверху вниз (сигнал на продажу)
Rule priceEmbedMa = new CrossedDownRule(priceIndicator, maIndicator);

// Быстрая скользящая средняя (EMA 9) пересекает медленную (EMA 21) сверху вниз
Rule deathCross = new CrossedDownRule(fastEma, slowEma);

// --- РАБОТА С БОЛЛИНДЖЕРОМ ---
// Цена пробила среднюю линию Боллинджера сверху вниз (Медвежий сигнал)
Rule priceDropsBelowBasis = new CrossedDownRule(bollinger);

// ПРИМЕР "Агрессивный выход":
// Цена упала ниже Боллинджера ИЛИ RSI пробил 70 вниз

// Достаем верхнюю линию Боллинджера как отдельный Indicator<Double>
BollingerBandsIndicator bollinger = new BollingerBandsIndicator(price, 20, 2);
Indicator<Double> upperBand = bollinger.getUpperBand();
Rule aggressiveExit = new CrossedDownRule(price, upperBand).or(new CrossedDownRule(rsi, 70.0));

*/

