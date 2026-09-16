package com.techmatrix18.trading.indicators;

import com.techmatrix18.trading.series.CandleSeries;

/**
 * BollingerIndicator calculates the middle band (SMA) of the Bollinger Bands based on a list of candles.
 * Индикатор тренда (Trend Indicators) - показывает силу и направление тренда.
 *
 * Средняя линия полос Боллинджера (фактически является SMA с периодом 20).
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class BollingerIndicator implements Indicator<BollingerValue> {
    private final int period;
    private final double standardDeviation;
    private BollingerValue[] historyCache = new BollingerValue[0]; // Инициализируем пустым массивом нулевой длины, чтобы избежать NullPointerException

    public BollingerIndicator(int period, double standardDeviation) {
        this.period = period;
        this.standardDeviation = standardDeviation;
    }

    public void prepare(CandleSeries series) {
        if (series == null || series.size() == 0) {
            historyCache = new BollingerValue[0];
            return;
        }

        int size = series.size();
        historyCache = new BollingerValue[size];

        for (int i = 0; i < size; i++) {
            if (i < period - 1) {
                historyCache[i] = BollingerValue.EMPTY;
                continue;
            }

            // 1. Считаем Среднюю линию (SMA) за период
            double sum = 0;
            for (int j = i; j > i - period; j--) {
                sum += series.getClose(j);
            }
            double sma = sum / period;

            // 2. Считаем Стандартное Отклонение (Sigma)
            double sumOfSquares = 0;
            for (int j = i; j > i - period; j--) {
                double diff = series.getClose(j) - sma;
                sumOfSquares += diff * diff;
            }
            double sigma = Math.sqrt(sumOfSquares / period);

            // 3. Записываем ВСЕ ТРИ уровня в кэш за один раз!
            double upper = sma + (standardDeviation * sigma);
            double lower = sma - (standardDeviation * sigma);

            historyCache[i] = new BollingerValue(upper, sma, lower);
        }
    }

    @Override
    public BollingerValue getValue(int index) {
        if (index < 0 || index >= historyCache.length || historyCache[index] == null) {
            return BollingerValue.EMPTY;
        }
        return historyCache[index];
    }

    // Метод calculate теперь просто берет данные из готового кэша или запускает prepare
    public BollingerValue calculate(CandleSeries series, int index) {
        if (historyCache.length <= index) {
            prepare(series);
        }
        return getValue(index);
    }
}

/*
Как использовать:

BollingerIndicator bb = new BollingerIndicator(20, 2.0);
bb.prepare(series);

// Создаем Indicator<Double> для конкретных линий Боллинджера "на лету":
Indicator<Double> upperLine = index -> bb.getValue(index).upper();
Indicator<Double> middleLine = index -> bb.getValue(index).middle();
Indicator<Double> lowerLine = index -> bb.getValue(index).lower();

// Цена (closePrice) пробивает среднюю линию (Basis) сверху вниз:
Rule priceDropsBelowBasis = new CrossedDownRule(closePrice, middleLine);

// Агрессивный выход: цена пересекает верхнюю линию Боллинджера сверху вниз
Rule aggressiveExit = new CrossedDownRule(closePrice, upperLine);

// Классическая трендовая стратегию — Вход на пробой границы канала + Фильтр тренда по EMA
// Логика стратегии:
// - Вход (BUY): Свеча закрывается выше верхней линии Боллинджера. Это означает, что на рынке начался сильный бычий
//   импульс (цена «идет по полосе»). Для фильтрации ложных сигналов мы добавим условие: цена должна быть выше EMA 200.
// - Выход (SELL): Цена падает и пробивает среднюю линию Боллинджера (Basis) сверху вниз. Это сигнал о том, что импульс
//   затух и тренд развернулся.

public void runBollingerStrategy(String symbol, CandleSeries series) {
    // 1. Защита: Боллинджеру нужно 20 свечей, но для EMA200 требуется минимум 200 свечей
    if (series.size() < 200) return;

    // 2. Инициализируем базовые индикаторы
    EmaIndicator ema200 = new EmaIndicator(200);
    BollingerIndicator bollinger = new BollingerIndicator(20, 2.0); // Период 20, 2 стандартных отклонения

    // Подготавливаем кэш истории (Каскадный вызов)
    ema200.prepare(series);
    bollinger.prepare(series);

    // 3. Выделяем конкретные линии Боллинджера как отдельные Indicator<Double> с помощью лямбд
    Indicator<Double> closePrice = index -> series.getClose(index);
    Indicator<Double> upperLine = index -> bollinger.getValue(index).upper();
    Indicator<Double> middleLine = index -> bollinger.getValue(index).middle();
    Indicator<Double> lowerLine = index -> bollinger.getValue(index).lower();

    // 4. Строим правила для ВХОДА (BUY)
    // Условие А: Цена пробивает Верхнюю ленту Боллинджера снизу вверх
    Rule bollingerBreakout = new CrossedUpRule(closePrice, upperLine);

    // Условие Б: Глобальный тренд восходящий (Цена выше EMA 200)
    Rule isBullish = new OverIndicatorRule(closePrice, ema200);

    // Объединяем: Входим только при пробое ВВЕРХ на бычьем рынке
    Rule entryRule = bollingerBreakout.and(isBullish);

    // 5. Строим правила для ВЫХОДА (SELL)
    // Выходим, когда цена пересекает Среднюю линию (Basis) сверху вниз
    Rule exitRule = new CrossedDownRule(closePrice, middleLine);

    // 6. Упаковываем в стратегию
    Strategy bbStrategy = new Strategy("Bollinger Breakout Trend", entryRule, exitRule, 2.0);

    System.out.println("=== СТАРТ БЭКТЕСТА БОЛЛИНДЖЕРА ДЛЯ " + symbol + " ===");

    // Начинаем с 200-й свечи, чтобы EMA200 полностью просчиталась
    for (int i = 200; i < series.size(); i++) {

        if (bbStrategy.shouldEnter(i)) {
            System.out.printf("🎯 [BUY] Свеча №%d | Цена пробила верхний Боллинджер! Цена: %.2f | Upper: %.2f%n",
                i, series.getClose(i), upperLine.getValue(i));

        } else if (bbStrategy.shouldExit(i)) {
            System.out.printf("🚨 [SELL] Свеча №%d | Цена упала ниже средней линии. Выход! Цена: %.2f | Middle: %.2f%n",
                i, series.getClose(i), middleLine.getValue(i));
        }
    }

    System.out.println("=== БЭКТЕСТ ЗАВЕРШЕН ===");
}

*/

