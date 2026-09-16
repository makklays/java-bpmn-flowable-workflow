package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.VolumeProfileIndicator;
import com.techmatrix18.trading.series.CandleSeries;

/**
 * PriceNearPOCRule checks if the current price is near the Point of Control (POC) level. (Point of Control)
 *
 * Уровень POC — это психологическая отметка, где в прошлом было совершено больше всего сделок.
 * Те, кто не успел купить там раньше, часто выставляют свои лимитные ордера на покупку именно на этот уровень
 * при его повторном тестировании.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class PriceNearPOCRule implements Rule {
    private final CandleSeries series; // Используем универсальный интерфейс
    private final VolumeProfileIndicator vpi;
    private final double sensitivityPercent;

    public PriceNearPOCRule(CandleSeries series, VolumeProfileIndicator vpi, double sensitivityPercent) {
        this.series = series;
        this.vpi = vpi;
        this.sensitivityPercent = sensitivityPercent;
    }

    @Override
    public boolean isSatisfied(int i) {
        double pocPrice = vpi.getValue(i);
        if (pocPrice <= 0) return false;

        // Получаем цену через метод нашей серии
        double currentPrice = series.getClose(i);

        // Проверяем близость цены к POC (Point of Control)
        double diff = Math.abs(currentPrice - pocPrice) / pocPrice * 100;
        return diff <= sensitivityPercent;
    }
}

/*
Как использовать:

// В PriceNearPOCRule мы передаем 50. Это значит, что бот разделит весь ценовой диапазон (от High до Low за
// выбранный период) на 50 горизонтальных уровней. Чем больше это число, тем точнее, но медленнее расчет.
Rule atPOC = new PriceNearPOCRule(volumeProfileIndicator, 50);

// Классическая синергия — Вход на отскок от POC при подтверждении Стохастика:
// Логика: Цена скорректировалась (упала) к самому сильному объемному уровню (POC), но не пробила его (находится
// около него). В этот же момент Стохастик выходит из зоны перепроданности, подтверждая появление покупателя.

public void runVolumeProfileStrategy(String symbol, CandleSeries series) {
    if (series.size() < 100) return;

    // 1. Инициализируем индикаторы
    VolumeProfileIndicator vpi = new VolumeProfileIndicator(100); // Профиль объема за 100 свечей
    StochasticIndicator stoch = new StochasticIndicator(14);

    vpi.prepare(series);
    stoch.prepare(series);

    // Линии Стохастика
    Indicator<Double> kLine = index -> stoch.getValue(index).k();
    Indicator<Double> dLine = index -> stoch.getValue(index).d();

    // 2. Строим правила
    // Условие А: Цена находится в пределах 0.5% от максимального объема (POC)
    Rule priceNearVolume = new PriceNearPOCRule(series, vpi, 0.5);

    // Условие Б: Стохастик дает бычий сигнал в зоне перепроданности
    Rule stochSignal = new CrossedUpRule(kLine, dLine).and(new UnderIndicatorRule(kLine, 20.0));

    // Объединяем: Вход строго у сильного уровня POC при развороте Стохастика
    Rule entryRule = priceNearVolume.and(stochSignal);

    // Выход: Стохастик зашел в перекупленность и развернулся вниз
    Rule exitRule = new CrossedDownRule(kLine, dLine).and(new OverIndicatorRule(kLine, 80.0));

    Strategy vpStrategy = new Strategy("Volume Profile + Stochastic", entryRule, exitRule);

    // 3. Цикл бэктестера
    for (int i = 100; i < series.size(); i++) {
        if (vpStrategy.shouldEnter(i)) {
            System.out.printf("🎯 [BUY] Свеча №%d | Вход возле POC (%.2f) по цене %.2f%n",
                i, vpi.getValue(i), series.getClose(i));
        } else if (vpStrategy.shouldExit(i)) {
            System.out.printf("🚨 [SELL] Свеча №%d | Выход по Стохастику по цене %.2f%n", i, series.getClose(i));
        }
    }
}

*/

