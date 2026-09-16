package com.techmatrix18.trading.indicators;

import com.techmatrix18.trading.series.CandleSeries;

/**
 * Interface for indicators, which calculates values based on a list of candles.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 *
 * @param <T>
 */

public interface Indicator<T> {
    /**
     * Рассчитывает значение индикатора для конкретной свечи по индексу.
     * Именно этот метод реализует математику индикатора.
     */
    default T calculate(CandleSeries series, int index) {
        return getValue(index);
    }

    /**
     * Возвращает заранее рассчитанное значение из кэша (history).
     * Используется правилами (Rule) для мгновенного доступа.
     */
    T getValue(int index);
}

