package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.FibLevels;
import com.techmatrix18.trading.indicators.FibonacciIndicator;
import com.techmatrix18.trading.series.CandleSeries;

import java.util.function.Function;

/**
 * PriceNearFibRule checks if the current price is near a specific Fibonacci level.
 * Правило близости цены к уровню Фибоначчи (Price Near Fibonacci Rule).
 *
 * Возвращает {@code true}, если текущая цена закрытия находится в пределах заданного
 * процентного диапазона (чувствительности) от выбранного уровня Фибоначчи.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class PriceNearFibRule implements Rule {
    private final CandleSeries series;
    private final FibonacciIndicator fib;

    /** Функциональный интерфейс для выбора уровня из FibLevels (например, FibLevels::lvl618) */
    private final Function<FibLevels, Double> levelExtractor;

    /** Чувствительность в виде доли (например, 0.005 для 0.5% отклонения) */
    private final double sensitivity;

    /**
     * Конструктор правила.
     *
     * @param series         история свечей
     * @param fib            подготовленный индикатор Фибоначчи
     * @param levelExtractor ссылка на метод уровня, например: {@code FibLevels::lvl618} или {@code FibLevels::lvl382}
     * @param sensitivity    допустимый процент отклонения (0.01 = 1%, 0.005 = 0.5%)
     */
    public PriceNearFibRule(CandleSeries series, FibonacciIndicator fib,
                            Function<FibLevels, Double> levelExtractor, double sensitivity) {
        this.series = series;
        this.fib = fib;
        this.levelExtractor = levelExtractor;
        this.sensitivity = sensitivity;
    }

    @Override
    public boolean isSatisfied(int i) {
        FibLevels levels = fib.getValue(i);

        // Защита от пустых данных на этапе прогрева индикатора
        if (levels == null || levels == FibLevels.EMPTY) {
            return false;
        }

        // Получаем цену текущей свечи из нашего интерфейса
        double currentPrice = series.getClose(i);

        // Извлекаем конкретный запрашиваемый уровень Фибоначчи через экстрактор
        double fibPrice = levelExtractor.apply(levels);

        // Математически точная проверка близости к уровню
        return Math.abs(currentPrice - fibPrice) / fibPrice <= sensitivity;
    }
}

/*
Как использовать:

FibonacciIndicator fib = new FibonacciIndicator(150);
fib.prepare(series);

// Ищем момент, когда цена подошла к Золотому Сечению (0.618) ближе чем на 0.3%
Rule nearGoldenRatio = new PriceNearFibRule(series, fib, FibLevels::lvl618, 0.003);

// Ищем момент, когда цена подошла к уровню 50% ближе чем на 0.5%
Rule nearFiftyPercent = new PriceNearFibRule(series, fib, FibLevels::lvl500, 0.005);

// Объединяем в общую стратегию
Rule entryRule = nearGoldenRatio.and(new MacdRule(macd, MacdRule.MacdCondition.CROSS_UP));

*/

