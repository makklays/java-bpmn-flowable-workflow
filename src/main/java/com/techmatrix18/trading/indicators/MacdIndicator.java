package com.techmatrix18.trading.indicators;

import com.techmatrix18.trading.series.CandleSeries;

/**
 * MacdIndicator is a placeholder for the actual implementation of the MACD (Moving Average Convergence Divergence) indicator.
 * MACD (Moving Average Convergence Divergence)
 * MACD состоит из трех компонентов:
 *     MACD Line: (EMA 12 - EMA 26).
 *     Signal Line: EMA 9 от самой линии MACD.
 *     Histogram: Разница между Линией и Сигналом.
 *
 * MACD — это уникальный «гибридный» индикатор, который сочетает в себе свойства трендового индикатора и осциллятора.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class MacdIndicator implements Indicator<MacdValue> {
    private final EmaIndicator fastEma;
    private final EmaIndicator slowEma;
    private final int signalPeriod;

    // Быстрый массив вместо List<Map>
    private MacdValue[] history = new MacdValue[0];

    public MacdIndicator(int fastPeriod, int slowPeriod, int signalPeriod) {
        this.fastEma = new EmaIndicator(fastPeriod);
        this.slowEma = new EmaIndicator(slowPeriod);
        this.signalPeriod = signalPeriod;
    }

    public void prepare(CandleSeries series) {
        if (series == null || series.size() == 0) {
            history = new MacdValue[0];
            return;
        }

        // 1. Подготавливаем базовые EMA линии
        fastEma.prepare(series);
        slowEma.prepare(series);

        int size = series.size();
        history = new MacdValue[size];

        // Шаг А: Сначала рассчитываем macdLine для всей истории
        double[] macdLines = new double[size];
        for (int i = 0; i < size; i++) {
            macdLines[i] = fastEma.getValue(i) - slowEma.getValue(i);
        }

        // Шаг Б: Рассчитываем стартовую точку для Signal Line (SMA от macdLines)
        double currentSignalLine = 0.0;
        if (size >= signalPeriod) {
            double sum = 0.0;
            for (int i = 0; i < signalPeriod; i++) {
                sum += macdLines[i];
            }
            currentSignalLine = sum / signalPeriod;
        }

        double multiplier = 2.0 / (signalPeriod + 1);

        // Шаг В: Заполняем историю с правильным расчетом EMA для Сигнальной линии
        for (int i = 0; i < size; i++) {
            // До достижения signalPeriod сигнальная линия считается нестабильной (или равной SMA)
            if (i >= signalPeriod) {
                currentSignalLine = (macdLines[i] - currentSignalLine) * multiplier + currentSignalLine;
            }

            double histogram = macdLines[i] - currentSignalLine;

            // Создаем чистый неизменяемый объект данных
            history[i] = new MacdValue(macdLines[i], currentSignalLine, histogram);
        }
    }

    @Override
    public MacdValue getValue(int index) {
        if (index < 0 || index >= history.length || history[index] == null) {
            return MacdValue.EMPTY;
        }
        return history[index];
    }

    @Override
    public MacdValue calculate(CandleSeries series, int index) {
        if (history.length <= index) {
            prepare(series);
        }
        return getValue(index);
    }
}

/*
Как использовать:

// Создаем нужные версии индикатора прямо в полях или конструкторе
private final MacdIndicator standardMacd = new MacdIndicator(12, 26, 9);
private final MacdIndicator fastMacd = new MacdIndicator(5, 13, 6);

// Вы просто создаете MACD. Внутри него уже сидят fastEma и slowEma
MacdIndicator macd = new MacdIndicator(12, 26, 9);

// Этот вызов автоматически подготовит и fastEma, и slowEma с правильной математикой!
macd.prepare(series);

// Дивергенция (Продвинутый уровень)
// Если цена ставит новый максимум, а MACD — нет, это «медвежья дивергенция» и сигнал о скором развороте тренда вниз.
// Это сложнее реализовать кодом (нужно хранить пики индикатора), но MACD для этого — лучший инструмент.

*/

