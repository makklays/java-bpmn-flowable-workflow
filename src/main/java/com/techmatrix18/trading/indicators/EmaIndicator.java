package com.techmatrix18.trading.indicators;

import com.techmatrix18.trading.series.CandleSeries;

/**
 * EmaIndicator calculates the Exponential Moving Average (EMA) for a given period.
 * Индикатор тренда (Trend Indicators) - показывает силу и направление тренда.
 *
 * EMA придает больше веса последним ценам, что делает его более чувствительным к новым данным по сравнению с SMA.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class EmaIndicator extends AbstractOscillator {

    private double[] historyCache = new double[0];

    public EmaIndicator(int period) {
        super(period);
    }

    @Override
    public void prepare(CandleSeries series) {
        if (series == null || series.size() == 0) {
            historyCache = new double[0];
            return;
        }

        int size = series.size();
        historyCache = new double[size];

        if (size < period) {
            // Если свечей меньше периода, EMA построить нельзя, заполняем ценой закрытия
            for (int i = 0; i < size; i++) historyCache[i] = series.getClose(i);
            return;
        }

        // Шаг 1: Первая точка EMA — это простая средняя (SMA) за первый период
        double sum = 0.0;
        for (int i = 0; i < period; i++) {
            sum += series.getClose(i);
            // До достижения полноценного периода записываем просто цену (или 0.0)
            historyCache[i] = series.getClose(i);
        }

        double ema = sum / period;
        historyCache[period - 1] = ema; // Записываем честную стартовую точку

        // Шаг 2: Для всех последующих свечей применяем рекуррентную формулу EMA
        double multiplier = 2.0 / (period + 1);
        for (int i = period; i < size; i++) {
            double close = series.getClose(i);
            ema = (close - ema) * multiplier + ema;
            historyCache[i] = ema;
        }
    }

    @Override
    public Double calculate(CandleSeries series, int index) {
        if (index < 0) return 0.0;

        // Если кэш подготовлен и индекс внутри него — мгновенно отдаем значение
        if (index < historyCache.length) {
            return historyCache[index];
        }

        // Если кэша нет, выполняем точно такой же математический расчет, как в prepare
        if (series.size() < period || index < period - 1) {
            return series.getClose(index);
        }

        double sum = 0.0;
        for (int i = 0; i < period; i++) {
            sum += series.getClose(i);
        }
        double ema = sum / period;

        double multiplier = 2.0 / (period + 1);
        for (int i = period; i <= index; i++) {
            ema = (series.getClose(i) - ema) * multiplier + ema;
        }

        return ema;
    }

    // Переопределяем getValue, чтобы он читал из нашего быстрого примитивного массива
    public double getEmaValue(int index) {
        if (index < 0 || index >= historyCache.length) return 0.0;
        return historyCache[index];
    }
}

/*
Как использовать:

// Инициализируем индикаторы
EmaIndicator ema9 = new EmaIndicator(9);
EmaIndicator ema21 = new EmaIndicator(21);

//
EmaIndicator ema200 = new EmaIndicator(200);
ema200.prepare(series);
Rule entryRule = new CrossedUpRule(closePrice, ema200);   // Цена пересекает EMA200 вверх
Rule exitRule = new CrossedDownRule(closePrice, ema200);   // Цена пересекает EMA200 вниз
Strategy trendStrategy = new Strategy("EMA Trend Strategy", entryRule, exitRule);

// Вы просто создаете MACD. Внутри него уже сидят fastEma и slowEma
MacdIndicator macd = new MacdIndicator(12, 26, 9);
macd.prepare(series);

// 1. Золотой крест (EMA 9 пересекает EMA 21 вверх)
Rule goldenCross = new IndicatorCrossedUpRule(ema9, ema21);

// 2. Смертельный крест (EMA 9 пересекает EMA 21 вниз)
Rule deathCross = new IndicatorCrossedDownRule(ema9, ema21);

if (goldenCross.isSatisfied(candles)) {
    telegramService.sendMessageForAll("🚀 Golden Cross! Быстрая EMA 9 пробила медленную EMA 21 вверх.");
}

*/

