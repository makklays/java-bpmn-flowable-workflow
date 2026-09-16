package com.techmatrix18.trading.series;

import com.techmatrix18.model.Candle;

/**
 * Candle Series -
 *
 * @author Alexander Kuziv
 * @since 14.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */
public interface CandleSeries {
    Candle getCandle(int index);
    int size();

    // Позволяет получить double значения конкретного поля свечи по индексу
    default double getClose(int index) { return getCandle(index).getClose().doubleValue(); }
    default double getHigh(int index) { return getCandle(index).getHigh().doubleValue(); }
    default double getLow(int index) { return getCandle(index).getLow().doubleValue(); }

    // Возвращает индекс самой последней свечи в списке
    default int getEndIndex() { return size() - 1; }
}

