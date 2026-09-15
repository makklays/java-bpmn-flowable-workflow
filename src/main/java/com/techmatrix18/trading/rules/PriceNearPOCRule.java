package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.VolumeProfileIndicator;
import com.techmatrix18.trading.series.CandleSeries;

/**
 * PriceNearPOCRule checks if the current price is near the Point of Control (POC) level.
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

*/

