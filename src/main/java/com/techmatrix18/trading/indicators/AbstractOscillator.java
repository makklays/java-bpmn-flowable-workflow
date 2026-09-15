package com.techmatrix18.trading.indicators;

import com.techmatrix18.trading.series.CandleSeries;

import java.util.ArrayList;
import java.util.List;

/**
 * AbstractOscillator serves as a base class for all oscillator indicators,
 * providing common functionality such as data validation and windowing.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */
public abstract class AbstractOscillator implements Indicator<Double> {
    protected final int period;
    protected List<Double> history = new ArrayList<>();

    protected AbstractOscillator(int period) {
        this.period = period;
    }

    // Рассчитывает историю один раз (для бэктеста или инициализации буфера)
    public void prepare(CandleSeries series) {
        history.clear();
        for (int i = 0; i < series.size(); i++) {
            // Теперь передаем серию и индекс напрямую
            history.add(calculate(series, i));
        }
    }

    @Override
    public Double getValue(int index) {
        if (index < 0 || index >= history.size()) return 0.0;
        return history.get(index);
    }

    // Проверка наличия данных по индексу
    protected boolean hasValue(int index) {
        return index >= period - 1;
    }

    // Главный метод: теперь он считает значение для КОНКРЕТНОЙ свечи 'index'
    // Это позволит не нарезать списки subList
    public abstract Double calculate(CandleSeries series, int index);
}

