package com.techmatrix18.trading.series;

import com.techmatrix18.model.Candle;

import java.util.List;

/**
 * Historical Candle Series -
 * классический паттерн проектирования, который идеально подходит для бэктестинга на готовых исторических данных
 * (загруженных из базы данных, CSV-файла или скачанных через API за прошлые периоды)
 *
 * @author Alexander Kuziv
 * @since 14.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class HistoricalCandleSeries implements CandleSeries {
    private final List<Candle> candles;

    public HistoricalCandleSeries(List<Candle> candles) {
        // Обертка List.copyOf гарантирует, что история не будет изменена извне во время теста
        this.candles = List.copyOf(candles);
    }

    @Override
    public Candle getCandle(int index) {
        return candles.get(index);
    }

    @Override
    public int size() {
        return candles.size();
    }

    @Override public double getClose(int index) { return candles.get(index).getClose().doubleValue(); }
    @Override public double getHigh(int index) { return candles.get(index).getHigh().doubleValue(); }
    @Override public double getLow(int index) { return candles.get(index).getLow().doubleValue(); }
}

