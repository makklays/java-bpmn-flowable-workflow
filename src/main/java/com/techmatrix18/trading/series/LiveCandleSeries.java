package com.techmatrix18.trading.series;

import com.techmatrix18.model.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Live Candle Series -
 *
 * Благодаря модификаторам synchronized, метод addCandle и методы чтения никогда не пересекутся некорректно,
 * и робот будет работать в онлайне стабильно неделями без вылетов.
 *
 * @author Alexander Kuziv
 * @since 14.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class LiveCandleSeries implements CandleSeries {

    // Используем ArrayList для быстрого доступа по индексу O(1)
    // Синхронизируем внутренний список для защиты от гонки потоков (Race Conditions)
    private final List<Candle> buffer = new ArrayList<>();
    private final int maxSize;

    public LiveCandleSeries(int maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * Потокобезопасное добавление новой свечи от биржевого веб-сокета.
     */
    public synchronized void addCandle(Candle candle) {
        // Если превысили лимит, удаляем самый старый элемент - защита от переполнения памяти IndexOutOfBoundsException
        if (buffer.size() >= maxSize) {
            buffer.remove(0);
        }

        buffer.add(candle);
    }

    @Override
    public synchronized Candle getCandle(int index) {
        return buffer.get(index);
    }

    @Override
    public synchronized int size() {
        return buffer.size();
    }

    @Override
    public synchronized double getClose(int index) { return buffer.get(index).getClose().doubleValue(); }

    @Override
    public synchronized double getHigh(int index) { return buffer.get(index).getHigh().doubleValue(); }

    @Override
    public synchronized double getLow(int index) { return buffer.get(index).getLow().doubleValue(); }
}

