package com.techmatrix18.trading.indicators;

import com.techmatrix18.trading.series.CandleSeries;

/**
 * FibonacciIndicator calculates Fibonacci retracement levels based on the high and low of a given list of candles.
 * Канальный и уровневый инструмент - показывает ключевые уровни поддержки и сопротивления, основанные на соотношениях Фибоначчи.
 *
 * Уровни коррекции Фибоначчи. Рассчитывает сетку уровней (0.236, 0.382, 0.5, 0.618, 0.786) на основе локальных максимумов и минимумов
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class FibonacciIndicator implements Indicator<FibLevels> {
    private final int period;
    private FibLevels[] historyCache = new FibLevels[0];

    public FibonacciIndicator(int period) {
        this.period = period;
    }

    public void prepare(CandleSeries series) {
        if (series == null || series.size() == 0) {
            historyCache = new FibLevels[0];
            return;
        }

        int size = series.size();
        historyCache = new FibLevels[size];

        // Заполняем историю. Первые (period-1) свечей не имеют полной истории, заполняем EMPTY или базой
        for (int i = 0; i < size; i++) {
            if (i < period - 1) {
                historyCache[i] = FibLevels.EMPTY;
                continue;
            }

            // Находим экстремумы и их индексы (чтобы понять направление тренда)
            int start = i - period + 1;
            int maxIdx = start;
            int minIdx = start;
            double high = series.getHigh(start);
            double low = series.getLow(start);

            for (int j = start + 1; j <= i; j++) {
                double currentHigh = series.getHigh(j);
                double currentLow = series.getLow(j);
                if (currentHigh > high) {
                    high = currentHigh;
                    maxIdx = j;
                }
                if (currentLow < low) {
                    low = currentLow;
                    minIdx = j;
                }
            }

            double diff = high - low;

            // Определяем направление тренда: кто был раньше?
            if (minIdx < maxIdx) {
                // Восходящий тренд (0% сверху на High, 100% снизу на Low)
                historyCache[i] = new FibLevels(
                        high,
                        high - (diff * 0.236),
                        high - (diff * 0.382),
                        high - (diff * 0.500),
                        high - (diff * 0.618),
                        high - (diff * 0.786),
                        low
                );
            } else {
                // Нисходящий тренд (0% снизу на Low, 100% сверху на High)
                historyCache[i] = new FibLevels(
                        low,
                        low + (diff * 0.236),
                        low + (diff * 0.382),
                        low + (diff * 0.500),
                        low + (diff * 0.618),
                        low + (diff * 0.786),
                        high
                );
            }
        }
    }

    @Override
    public FibLevels getValue(int index) {
        if (index < 0 || index >= historyCache.length || historyCache[index] == null) {
            return FibLevels.EMPTY;
        }
        return historyCache[index];
    }

    @Override
    public FibLevels calculate(CandleSeries series, int index) {
        if (historyCache.length <= index) {
            prepare(series);
        }
        return getValue(index);
    }
}

/*
Как использовать:

// правило (например, FibReboundRule), которое проверяет, отскочила ли цена от золотого сечения 0.618
FibonacciIndicator fib = new FibonacciIndicator(120);
fib.prepare(series);
// Ваше правило сможет красиво и быстро читать уровни:
double goldenLevel = fib.getValue(i).lvl618();

// !!!
// Классическая и прибыльная стратегию: Вход на отскоке от «Золотого Сечения» (0.618) по тренду

// Логика стратегии
// 1. Глобальный тренд — бычий: Цена находится выше тяжелой EMA 200.
// 2. Откат к уровню Фибоначчи: Цена падает (корректируется) сверху вниз и касается уровня 0.618 (или 0.500).
// 3. Сигнал на вход (BUY): Быстрая EMA 9 пересекает EMA 21 снизу вверх, подтверждая, что отскок от уровня Фибоначчи состоялся и цена снова растет.
// 4. Выход (SELL): Обратное пересечение средних или достижение уровня 0% (предыдущего максимума).

public void runFibonacciTrendStrategy(String symbol, CandleSeries series) {
    // Нам нужен глубокий период истории для разметки Фибо-сетки (например, 150 свечей)
    // и для стабильности тяжелой EMA 200
    if (series.size() < 200) return;

    // 1. Инициализируем и подготавливаем индикаторы
    Indicator<Double> closePrice = index -> series.getClose(index);

    EmaIndicator ema9 = new EmaIndicator(9);
    EmaIndicator ema21 = new EmaIndicator(21);
    EmaIndicator ema200 = new EmaIndicator(200);
    FibonacciIndicator fib = new FibonacciIndicator(150); // Ищем экстремумы за последние 150 свечей

    // Каскадный вызов подготовки кэша
    ema9.prepare(series);
    ema21.prepare(series);
    ema200.prepare(series);
    fib.prepare(series);

    // 2. Строим логические правила для ВХОДА (BUY)
    Rule isBullishTrend = new OverIndicatorRule(closePrice, ema200); // Цена выше EMA 200
    Rule fibRebound = new FibSupportReboundRule(series, fib);         // Цена отскочила от зоны 0.500-0.618
    Rule emaCrossUp = new CrossedUpRule(ema9, ema21);                // EMA 9 пробила EMA 21 вверх

    // Объединяем условия: ТРЕНД + ОТСКАТ К ФИБО + РАЗВОРOT СРЕДНИХ
    Rule entryRule = isBullishTrend.and(fibRebound).and(emaCrossUp);

    // 3. Строим правила для ВЫХОДА (SELL)
    Rule exitRule = new CrossedDownRule(ema9, ema21); // Выходим, когда краткосрочный тренд развернулся вниз

    // 4. Упаковываем в стратегию
    Strategy fibStrategy = new Strategy("Fibonacci Gold Rebound", entryRule, exitRule, 1.5);

    System.out.println("=== СТАРТ ФИБО-БЭКТЕСТА ДЛЯ " + symbol + " ===");

    // Начинаем с 200 свечи, чтобы все индикаторы гарантированно накопили историю
    for (int i = 200; i < series.size(); i++) {

        if (fibStrategy.shouldEnter(i)) {
            System.out.printf("🎯 [BUY] Свеча №%d | Цена: %.2f | Отскок от Фибо 0.618: %.2f%n",
                i, series.getClose(i), fib.getValue(i).lvl618());

        } else if (fibStrategy.shouldExit(i)) {
            System.out.printf("🚨 [SELL] Свеча №%d | Цена: %.2f | Выход по пересечению EMA%n",
                i, series.getClose(i));
        }
    }

    System.out.println("=== БЭКТЕСТ ЗАВЕРШЕН ===");
}

*/

