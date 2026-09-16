package com.techmatrix18.trading.indicators;

import com.techmatrix18.trading.series.CandleSeries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SupportResistanceIndicator is a placeholder for an indicator that identifies support and resistance levels in price data.
 * Индикатор уровней поддержки и сопротивления (Support and Resistance Indicators) - помогает выявить ключевые уровни, где цена может отскочить или пробиться.
 *
 * Индикатор ищет уровни, объединяет их в «зоны» и считает количество подтверждений (касаний).
 *
 * Индикатор Уровней Поддержки и Сопротивления (Фрактальные Уровни)</b>.
 * Находит сильные ценовые зоны на основе фракталов Билла Вильямса и фиксирует количество их касаний.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class SupportResistanceIndicator implements Indicator<List<SupportResistanceIndicator.Level>> {

    // Кэшируем список подтвержденных уровней для каждой точки истории
    private final List<List<Level>> historyCache = new ArrayList<>();

    // Параметр чувствительности теперь хранится внутри класса
    private final double sensitivity;

    /**
     * Конструктор индикатора уровней.
     * @param sensitivity Процентная чувствительность объединения линий (например, 0.5 для 0.5%)
     */
    public SupportResistanceIndicator(double sensitivity) {
        this.sensitivity = sensitivity;
    }

    /**
     * ИСПРАВЛЕНО: Метод теперь принимает строго ОДИН аргумент, как и ожидалось в calculate.
     */
    public void prepare(CandleSeries series) {
        historyCache.clear();
        if (series == null || series.size() == 0) return;

        List<Level> currentLevels = new ArrayList<>();

        for (int i = 0; i < series.size(); i++) {
            // Фрактал (с плечом 2) формируется на свече (i - 2).
            int fractalIdx = i - 2;

            // Защита от заглядывания в будущее
            if (fractalIdx >= 2 && fractalIdx <= i - 2) {
                if (isLowFractal(series, fractalIdx)) {
                    // Используем sensitivity из поля класса
                    addPointToLevels(currentLevels, series.getLow(fractalIdx), sensitivity);
                }
                if (isHighFractal(series, fractalIdx)) {
                    addPointToLevels(currentLevels, series.getHigh(fractalIdx), sensitivity);
                }
            }

            // Фильтруем только подтвержденные уровни (где касаний >= 2)
            List<Level> confirmedLevels = new ArrayList<>(currentLevels.size());
            for (Level l : currentLevels) {
                if (l.getTouches() >= 2) {
                    confirmedLevels.add(l.copy());
                }
            }
            historyCache.add(confirmedLevels);
        }
    }

    @Override
    public List<Level> getValue(int index) {
        if (index < 0 || index >= historyCache.size()) {
            return Collections.emptyList();
        }
        return historyCache.get(index);
    }

    @Override
    public List<Level> calculate(CandleSeries series, int index) {
        if (historyCache.size() <= index) {
            prepare(series); // Теперь здесь строго один аргумент!
        }
        return getValue(index);
    }

    private void addPointToLevels(List<Level> levels, double price, double sensitivity) {
        boolean found = false;
        for (Level l : levels) {
            if (Math.abs(l.getPrice() - price) / price * 100.0 < sensitivity) {
                l.addTouch();
                found = true;
                break;
            }
        }
        if (!found) {
            levels.add(new Level(price));
        }
    }

    private boolean isLowFractal(CandleSeries series, int i) {
        double p = series.getLow(i);
        return p < series.getLow(i-1) && p < series.getLow(i-2) &&
                p < series.getLow(i+1) && p < series.getLow(i+2);
    }

    private boolean isHighFractal(CandleSeries series, int i) {
        double p = series.getHigh(i);
        return p > series.getHigh(i-1) && p > series.getHigh(i-2) &&
                p > series.getHigh(i+1) && p > series.getHigh(i+2);
    }

    /**
     * Контейнер горизонтального уровня цены.
     */
    public static class Level {
        private final double price;
        private int touches;

        public Level(double price) {
            this.price = price;
            this.touches = 1;
        }

        public Level(double price, int touches) {
            this.price = price;
            this.touches = touches;
        }

        public void addTouch() { this.touches++; }
        public double getPrice() { return price; }
        public int getTouches() { return touches; }

        public Level copy() {
            return new Level(this.price, this.touches);
        }
    }
}

/*
Как использовать:

Автоматические уровни позволяют создать очень мощное правило:
«Покупать только если цена пробила среднюю Боллинджера И находится прямо над сильным уровнем поддержки».

// Создаем индикатор с шагом объединения уровней в 0.5%
SupportResistanceIndicator srIndicator = new SupportResistanceIndicator(0.5);

// Запускаем расчет истории
srIndicator.prepare(series);

// В цикле бэктестера извлекаем подтвержденные уровни для текущей свечи
List<SupportResistanceIndicator.Level> levelsAtCurrentBar = srIndicator.getValue(i);


*/

