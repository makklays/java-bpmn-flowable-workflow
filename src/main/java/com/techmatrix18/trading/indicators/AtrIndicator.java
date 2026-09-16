package com.techmatrix18.trading.indicators;

import com.techmatrix18.trading.series.CandleSeries;

/**
 * ATR (Average True Range) - это индикатор, который измеряет волатильность рынка, показывая среднее значение истинного
 * диапазона за определенный период времени. ATR помогает трейдерам оценить уровень риска и определить оптимальные уровни стоп-лоссов.
 *
 * Индикатор Среднего Истинного Диапазона (ATR)
 * Использует классическое сглаживание Дж. Уоллеса Уайлдера (RMA) и примитивный кэш
 * для обеспечения максимальной точности и скорости бэктестинга O(1).
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class AtrIndicator implements Indicator<Double> {
    private final int period;
    private double[] historyCache = new double[0];

    public AtrIndicator(int period) {
        this.period = period;
    }

    public void prepare(CandleSeries series) {
        if (series == null || series.size() == 0) {
            historyCache = new double[0];
            return;
        }

        int size = series.size();
        historyCache = new double[size];

        if (size <= period) {
            return; // Недостаточно данных для расчета даже базового окна
        }

        // Временный массив для хранения чистых значений True Range
        double[] trueRanges = new double[size];

        // Шаг 1: Считаем True Range для всей истории (начиная с 1-й свечи, так как нужна prevClose)
        for (int i = 1; i < size; i++) {
            double high = series.getHigh(i);
            double low = series.getLow(i);
            double prevClose = series.getClose(i - 1);

            trueRanges[i] = Math.max(high - low,
                    Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        }

        // Шаг 2: Находим первую точку ATR как простую среднюю (SMA) за первый доступный период
        double sumTR = 0;
        for (int i = 1; i <= period; i++) {
            sumTR += trueRanges[i];
        }

        double currentAtr = sumTR / period;
        historyCache[period] = currentAtr;

        // Шаг 3: Для всех последующих свечей применяем рекуррентное сглаживание Уайлдера (RMA)
        for (int i = period + 1; i < size; i++) {
            currentAtr = (currentAtr * (period - 1) + trueRanges[i]) / period;
            historyCache[i] = currentAtr;
        }
    }

    @Override
    public Double getValue(int index) {
        if (index < 0 || index >= historyCache.length) {
            return 0.0;
        }
        return historyCache[index];
    }

    @Override
    public Double calculate(CandleSeries series, int index) {
        if (historyCache.length <= index) {
            prepare(series);
        }
        return getValue(index); // Java автоматически обернет примитив из массива в Double (Autoboxing)
    }

    /**
     * Быстрый метод для получения значения ATR в виде примитива double без автобоксинга.
     */
    public double getAtrValue(int index) {
        if (index < 0 || index >= historyCache.length) {
            return 0.0;
        }
        return historyCache[index];
    }
}

/*
Как использовать:

Трейдеры часто ставят стоп на расстоянии 2 * ATR от цены входа. Теперь вы можете это автоматизировать:
double currentAtr = atr.calculate(candles);
double entryPrice = candles.get(0).getClose().doubleValue();

// Динамический стоп-лосс (на 2 волатильности ниже входа)
double stopPrice = entryPrice - (currentAtr * 2);

// Правило выхода, которое рассчитывает стоп-лосс на расстоянии 3-х ATR от цены входа. Если цена падает ниже этого
// динамического уровня, позиция закрывается.

// Пример логики расчета лота:
double riskAmountUSD = 50.0; // Мы готовы потерять максимум $50 на этой сделке
double currentAtr = atr.getAtrValue(index);
double stopDistanceUSD = 3.0 * currentAtr; // Расстояние до стопа

// Робот динамически вычисляет размер позиции в штуках/монетах перед отправкой на биржу
double positionSize = riskAmountUSD / stopDistanceUSD;

//
public void runStrategyWithAtrRisk(String symbol, CandleSeries series) {
    // 1. Защита: для ATR(14) и фильтра тренда EMA(200) нужно минимум 200 свечей
    if (series.size() < 200) return;

    // 2. Инициализируем индикаторы
    Indicator<Double> closePrice = index -> series.getClose(index);
    EmaIndicator ema200 = new EmaIndicator(200);
    AtrIndicator atr = new AtrIndicator(14); // Классический период волатильности 14

    // Готовим кэш истории
    ema200.prepare(series);
    atr.prepare(series);

    // 3. Базовые торговые правила (например, простая трендовая стратегия)
    EmaIndicator fastEma = new EmaIndicator(9);
    EmaIndicator slowEma = new EmaIndicator(21);
    fastEma.prepare(series);
    slowEma.prepare(series);

    Rule entryRule = new CrossedUpRule(fastEma, slowEma)
            .and(new OverIndicatorRule(closePrice, ema200)); // Покупаем только по глобальному тренду

    // Ссылка на текущую открытую сделку (holder)
    TradeSession[] activeSession = new TradeSession;

    // 4. ИСПОЛЬЗОВАНИЕ ATR: Динамическое правило Стоп-Лосса "на лету"
    Rule atrStopLossRule = index -> {
        if (activeSession == null) return false;

        double entryPrice = activeSession.getEntryPrice();
        double currentAtr = atr.getAtrValue(index); // Берем быстрое примитивное значение

        // Формула: Стоп-лосс находится ниже цены входа на (3 * Волатильность)
        // На спокойном рынке ATR маленький -> стоп близко. На шторме ATR большой -> стоп далеко.
        double stopLevel = entryPrice - (3.0 * currentAtr);

        // Сигнал на выход, если цена закрытия пробила наш динамический стоп
        return series.getClose(index) <= stopLevel;
    };

    // Технический выход по индикаторам (EMA развернулись) ИЛИ выбило по динамическому ATR-стопу
    Rule finalExitRule = new CrossedDownRule(fastEma, slowEma).or(atrStopLossRule);

    Strategy atrStrategy = new Strategy("EMA Cross + ATR Risk", entryRule, finalExitRule, 0.0);

    System.out.println("=== СТАРТ БЭКТЕСТА С ATR-РИСКАМИ ДЛЯ " + symbol + " ===");

    // 5. Цикл симулятора сделок
    for (int i = 200; i < series.size(); i++) {
        if (activeSession == null) {
            // Мы вне рынка — ищем точку входа
            if (atrStrategy.shouldEnter(i)) {
                activeSession = new TradeSession(series.getClose(i));
                System.out.printf("🎯 [ВХОД BUY] Свеча №%d | Цена входа: %.2f | Текущий ATR: %.2f%n",
                        i, series.getClose(i), atr.getAtrValue(i));
            }
        } else {
            // Мы в сделке — проверяем условия выхода
            if (atrStrategy.shouldExit(i)) {

                // Проверяем, вышла ли стратегия конкретно по стопу волатильности
                if (atrStopLossRule.isSatisfied(i)) {
                    System.out.printf("💸 [ВЫХОД ПО ATR STOP] Свеча №%d | Рыночный шум превысил 3*ATR. Позиция ликвидирована по цене: %.2f%n",
                            i, series.getClose(i));
                } else {
                    System.out.printf("🚨 [ВЫХОД ТЕХНИЧЕСКИЙ] Свеча №%d | Тренд изменился, плановый выход по цене: %.2f%n",
                            i, series.getClose(i));
                }

                activeSession = null; // Закрываем позицию
            }
        }
    }
    System.out.println("=== БЭКТЕСТ ЗАВЕРШЕН ===");
}

*/

