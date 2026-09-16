package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.MacdIndicator;
import com.techmatrix18.trading.rules.Rule;
import com.techmatrix18.trading.series.CandleSeries;

/**
 * MacdBearishDivergenceRule
 * Когда: Цена растет на графике, но дивергенция на MACD показывает, что будет разворот цены
 *
 * Цена (High):        Пик 2 (Старый)               Пик 1 (Новый, ВЫШЕ)
 *                        / \                          / \
 *                       /   \                        /   \
 * --------------------------------------------------------------------> Время
 *
 * MACD Line:          Пик 2 (Старый, ВЫШЕ)
 *                        / \
 *                       /   \                        / \
 *                                                   /   \ Пик 1 (Новый, НИЖЕ)
 *
 * @author Alexander Kuziv
 * @since 16.09.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class MacdBearishDivergenceRule implements Rule {

    private final CandleSeries series;
    private final MacdIndicator macd;
    private final int lookback = 3; // Количество свечей вокруг пика для его подтверждения
    private final int maxSearchRange = 60; // Максимальное расстояние между двумя пиками (история поиска)

    public MacdBearishDivergenceRule(CandleSeries series, MacdIndicator macd) {
        this.series = series;
        this.macd = macd;
    }

    @Override
    public boolean isSatisfied(int i) {
        // Дивергенция требует глубокой истории для поиска двух пиков
        if (i < maxSearchRange + lookback) return false;

        // ВАЖНО: Так как пик подтверждается только после закрытия `lookback` свечей справа,
        // мы ищем первый (самый свежий) пик, начиная с индекса (i - lookback).
        int firstPeakIdx = findLastPricePeak(i - lookback);
        if (firstPeakIdx == -1) return false;

        // Ищем второй (более старый) пик левее первого
        int secondPeakIdx = findLastPricePeak(firstPeakIdx - 1);
        if (secondPeakIdx == -1) return false;

        // Проверяем условие дивергенции по ЦЕНЕ (Цена должна расти: новый пик выше старого)
        double firstPricePeak = series.getHigh(firstPeakIdx);
        double secondPricePeak = series.getHigh(secondPeakIdx);

        if (firstPricePeak <= secondPricePeak) {
            return false; // Цена не сделала более высокий максимум (Higher High)
        }

        // Проверяем условие по MACD (Линия MACD должна падать: новый пик индикатора НИЖЕ старого)
        double firstMacdPeak = macd.getValue(firstPeakIdx).macdLine();
        double secondMacdPeak = macd.getValue(secondPeakIdx).macdLine();

        // Сигнал истинен, если новый пик MACD ниже предыдущего
        return firstMacdPeak < secondMacdPeak;
    }

    /**
     * Ищет индекс последнего подтвержденного экстремума (максимума цены)
     * двигаясь справа налево по истории.
     */
    private int findLastPricePeak(int startIdx) {
        int limit = Math.max(lookback, startIdx - maxSearchRange);

        for (int p = startIdx; p >= limit; p--) {
            if (isPricePeak(p)) {
                return p;
            }
        }
        return -1; // Пик не найден в заданном диапазоне
    }

    /**
     * Математическое определение пика: точка выше своих соседей слева и справа.
     */
    private boolean isPricePeak(int index) {
        double targetHigh = series.getHigh(index);

        // Проверяем N соседей слева и N соседей справа
        for (int offset = 1; offset <= lookback; offset++) {
            if (series.getHigh(index - offset) >= targetHigh) return false;
            if (series.getHigh(index + offset) >= targetHigh) return false;
        }
        return true;
    }
}

/*
Как использовать:

public void runMyLogic(String symbol, CandleSeries series) {
    if (series.size() < 200) return;

    // Инициализируем индикаторы
    MacdIndicator macdIndicator = new MacdIndicator(12, 26, 9);
    macdIndicator.prepare(series);

    // Правило входа (например, обычное пересечение средних)
    Rule entryRule = new IndicatorCrossedUpRule(fastSma, slowSma);

    // Продвинутое правило выхода — МЕДВЕЖЬЯ ДИВЕРГЕНЦИЯ!
    Rule exitRule = new MacdBearishDivergenceRule(series, macdIndicator);

    Strategy divergenceStrategy = new Strategy("MACD Divergence Exit", entryRule, exitRule);

    for (int i = 100; i < series.size(); i++) {
        if (divergenceStrategy.shouldEnter(i)) {
            System.out.printf("🎯 ВХОД | Свеча №%d | Цена: %.2f%n", i, series.getClose(i));
        } else if (divergenceStrategy.shouldExit(i)) {
            // Сигнал сработает на свече i, обнаружив пик, который зафиксировался lookback свечей назад
            System.out.printf("🚨 ВЫХОД (ДИВЕРГЕНЦИЯ!) | Свеча №%d | Цена: %.2f%n", i, series.getClose(i));
        }
    }
}

*/