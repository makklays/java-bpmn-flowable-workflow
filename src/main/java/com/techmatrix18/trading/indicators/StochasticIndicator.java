package com.techmatrix18.trading.indicators;

import com.techmatrix18.trading.series.CandleSeries;

/**
 * StochasticIndicator calculates the Stochastic Oscillator, which is a momentum indicator comparing a particular
 * closing price of a security to a range of its prices over a certain period of time.
 * Осциллятор (Oscillator) - от 0 до 100, показывает перекупленность/перепроданность актива.
 *
 * Показывает перекупленность/перепроданность актива, помогает находить точки разворота тренда.
 *
 * Зона перепроданности (< 20): Когда рынок падает слишком сильно, линии Стохастика уходят ниже 20.
 * Как только быстрая линия %K пересекает сигнальную %D снизу вверх — это сильный сигнал на покупку (BUY)
 *
 * Зона перекупленности (> 80): Когда рынок перегрет, линии уходят выше 80. Пересечение %K и %D
 * сверху вниз — это сигнал на продажу/выход (SELL).
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class StochasticIndicator implements Indicator<StochasticValue> {
    private final int kPeriod;
    private final int dPeriod = 3;
    private StochasticValue[] historyCache = new StochasticValue[0];

    public StochasticIndicator() {
        this.kPeriod = 14;
    }

    public StochasticIndicator(int kPeriod) {
        this.kPeriod = kPeriod;
    }

    public void prepare(CandleSeries series) {
        if (series == null || series.size() == 0) {
            historyCache = new StochasticValue[0];
            return;
        }

        int size = series.size();
        historyCache = new StochasticValue[size];

        // Массив для временного хранения быстрой линии %K перед сглаживанием
        double[] kLines = new double[size];

        // Шаг 1: Считаем быструю линию %K
        for (int i = 0; i < size; i++) {
            if (i < kPeriod - 1) {
                kLines[i] = 50.0; // Нейтральная зона на этапе прогрева
                continue;
            }

            int start = i - kPeriod + 1;
            double lowMin = series.getLow(start);
            double highMax = series.getHigh(start);

            for (int j = start + 1; j <= i; j++) {
                double currentLow = series.getLow(j);
                double currentHigh = series.getHigh(j);
                if (currentLow < lowMin) lowMin = currentLow;
                if (currentHigh > highMax) highMax = currentHigh;
            }

            double currentClose = series.getClose(i);

            if (highMax == lowMin) {
                kLines[i] = 50.0;
            } else {
                kLines[i] = ((currentClose - lowMin) / (highMax - lowMin)) * 100.0;
            }
        }

        // Шаг 2: Считаем сигнальную линию %D (SMA от %K) и упаковываем в кэш
        for (int i = 0; i < size; i++) {
            if (i < kPeriod - 1 + dPeriod - 1) {
                // Ждем пока прогреется и %K, и скользящая средняя %D
                historyCache[i] = StochasticValue.NEUTRAL;
                continue;
            }

            double sum = 0;
            for (int j = 0; j < dPeriod; j++) {
                sum += kLines[i - j];
            }
            double dLine = sum / dPeriod;

            historyCache[i] = new StochasticValue(kLines[i], dLine);
        }
    }

    @Override
    public StochasticValue getValue(int index) {
        if (index < 0 || index >= historyCache.length || historyCache[index] == null) {
            return StochasticValue.NEUTRAL;
        }
        return historyCache[index];
    }

    public StochasticValue calculate(CandleSeries series, int index) {
        if (historyCache.length <= index) {
            prepare(series);
        }
        return getValue(index);
    }
}


/*
Как использовать:

public void runStochasticStrategy(String symbol, CandleSeries series) {
    // 1. Защита: Стохастику (14, 3) нужно минимум 17-20 свечей для прогрева истории
    if (series.size() < 30) return;

    // 2. Инициализируем индикатор Стохастика (период %K = 14)
    StochasticIndicator stochastic = new StochasticIndicator(14);

    // Подготавливаем кэш истории (заполняем массив historyCache за один проход)
    stochastic.prepare(series);

    // 3. Выделяем конкретные линии Стохастика как отдельные Indicator<Double> с помощью лямбд
    Indicator<Double> kLine = index -> stochastic.getValue(index).k();
    Indicator<Double> dLine = index -> stochastic.getValue(index).d();

    // 4. Строим правила для ВХОДА (BUY)
    // Условие А: Линия %K пересекает сигнальную линию %D снизу вверх
    Rule stochCrossUp = new CrossedUpRule(kLine, dLine);
    // Условие Б: Линия %K находится в зоне перепроданности (ниже 20)
    Rule inOversoldZone = new UnderIndicatorRule(kLine, 20.0);

    // Объединяем: Входим, когда пересечение происходит строго в зоне перепроданности
    Rule entryRule = stochCrossUp.and(inOversoldZone);

    // 5. Строим правила для ВЫХОДА (SELL)
    // Условие А: Линия %K пересекает %D сверху вниз
    Rule stochCrossDown = new CrossedDownRule(kLine, dLine);
    // Условие Б: Линия %K находится в зоне перекупленности (выше 80)
    Rule inOverboughtZone = new OverIndicatorRule(kLine, 80.0);

    // Выходим, когда пересечение происходит в зоне перекупленности
    Rule exitRule = stochCrossDown.and(inOverboughtZone);

    // 6. Упаковываем правила в стратегию
    Strategy stochStrategy = new Strategy("Stochastic Oscillator Rebound", entryRule, exitRule);

    System.out.println("=== СТАРТ БЭКТЕСТА СТОХАСТИКА ДЛЯ " + symbol + " ===");

    // Начинаем с 20-й свечи, так как к этому моменту индикатор уже стабилен
    for (int i = 20; i < series.size(); i++) {

        if (stochStrategy.shouldEnter(i)) {
            System.out.printf("🎯 [BUY] Свеча №%d | Бычий разворот Стохастика! %%K: %.2f | %%D: %.2f%n",
                i, kLine.getValue(i), dLine.getValue(i));

        } else if (stochStrategy.shouldExit(i)) {
            System.out.printf("🚨 [SELL] Свеча №%d | Медвежий разворот Стохастика! Выход! %%K: %.2f | %%D: %.2f%n",
                i, kLine.getValue(i), dLine.getValue(i));
        }
    }

    System.out.println("=== БЭКТЕСТ ЗАВЕРШЕН ===");
}

*/

