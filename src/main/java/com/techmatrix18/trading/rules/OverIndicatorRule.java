package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.Indicator;

/**
 * OverIndicatorRule checks if the value of a specified indicator is above a certain threshold.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class OverIndicatorRule implements Rule {
    private final Indicator<Double> indicator;
    private final double threshold;

    public OverIndicatorRule(Indicator<Double> indicator, double threshold) {
        this.indicator = indicator;
        this.threshold = threshold;
    }

    @Override
    public boolean isSatisfied(int i) {
        // Просто берем значение индикатора по индексу i
        // Метод getValue(i) работает мгновенно, так как берет данные из кэша
        return indicator.getValue(i) > threshold;
    }
}

/*
Как использовать:

// --- ВЫШЕ УРОВНЯ (OverIndicatorRule) ---
// RSI находится в зоне перекупленности (выше 70)
Rule rsiIsHigh = new OverIndicatorRule(rsi, 70.0);

// Цена находится выше определенного уровня Фибоначчи
Rule priceAboveFib = new OverIndicatorRule((c) -> fib.calculate(c).get("level_618"), 0.0);

//
Indicator<Double> closePrice = index -> series.getClose(index);
EmaIndicator ema200 = new EmaIndicator(200);
ema200.prepare(series);

// ТРЕНД: Цена выше EMA 200
Rule isBullishTrend = new OverIndicatorRule(closePrice, ema200);

// ВЫХОД: RSI поднялся выше 70
Rule rsiOverbought = new OverIndicatorRule(rsiIndicator, 70.0);

Indicator<Double> kLine = index -> stochastic.getValue(index).k();
// Рынок перегрет, %K зашла в зону выше 80
Rule stochOverbought = new OverIndicatorRule(kLine, 80.0);

//
public void runOverIndicatorRuleExample(String symbol, CandleSeries series) {
    if (series.size() < 200) return;

    // 1. Инициализация индикаторов
    Indicator<Double> closePrice = index -> series.getClose(index);
    EmaIndicator fastEma = new EmaIndicator(9);
    EmaIndicator slowEma = new EmaIndicator(21);
    EmaIndicator trendEma = new EmaIndicator(200); // Фильтр тренда
    StochasticIndicator stoch = new StochasticIndicator(14);

    // Подготовка кэша
    fastEma.prepare(series);
    slowEma.prepare(series);
    trendEma.prepare(series);
    stoch.prepare(series);

    // Выделяем линии Стохастика
    Indicator<Double> kLine = index -> stoch.getValue(index).k();
    Indicator<Double> dLine = index -> stoch.getValue(index).d();

    // 2. Строим правила для ВХОДА (BUY)
    Rule maCrossUp = new CrossedUpRule(fastEma, slowEma);

    // ИСПОЛЬЗОВАНИЕ ПРАВИЛА: Входим только если цена НАД трендовой EMA 200
    Rule filterTrend = new OverIndicatorRule(closePrice, 200.0); // Передаем индикатор цены и уровень/индикатор EMA
    // Если OverIndicatorRule принимает (Indicator, Indicator), то: new OverIndicatorRule(closePrice, trendEma)

    Rule entryRule = maCrossUp.and(filterTrend);

    // 3. Строим правила для ВЫХОДА (SELL)
    Rule stochCrossDown = new CrossedDownRule(kLine, dLine);

    // ИСПОЛЬЗОВАНИЕ ПРАВИЛА: Выходим, только если Стохастик находится в зоне перекупленности (> 80)
    Rule highZone = new OverIndicatorRule(kLine, 80.0);

    Rule exitRule = stochCrossDown.and(highZone);

    // 4. Упаковываем в стратегию
    Strategy myStrategy = new Strategy("Trend MA + Stoch Exit", entryRule, exitRule, 2.0);

    // 5. Прогон по истории
    for (int i = 200; i < series.size(); i++) {
        if (myStrategy.shouldEnter(i)) {
            System.out.printf("🎯 [BUY] Свеча №%d | Тренд подтвержден (Цена выше EMA200). Цена: %.2f%n",
                i, series.getClose(i));
        } else if (myStrategy.shouldExit(i)) {
            System.out.printf("🚨 [SELL] Свеча №%d | Выход из перекупленности Стохастика (>80). Цена: %.2f%n",
                i, series.getClose(i));
        }
    }
}

*/

