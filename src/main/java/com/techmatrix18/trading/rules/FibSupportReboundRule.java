package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.FibLevels;
import com.techmatrix18.trading.indicators.FibonacciIndicator;
import com.techmatrix18.trading.series.CandleSeries;

/**
 * FibSupportReboundRule - Правило отскока от уровня поддержки Фибоначчи (Fibonacci Support Rebound Rule).
 *
 * Используется в торговых стратегиях для подтверждения разворота цены вверх после локальной коррекции.
 *
 * Логика работы:
 * Правило сканирует последние {@code checkWindow} свечей (включая текущую) и ищет классический паттерн "ложного пробоя" зоны поддержки:
 * - Тень свечи (Low) должна опуститься ниже психологического уровня 50.0% (lvl500), что подтверждает глубокий тест зоны покупателей.
 * - Тело свечи (Close) при этом должно закрыться ВЫШЕ "Золотого Сечения" 61.8% (lvl618).
 * Такое поведение графика говорит о сильном давлении покупателей на уровнях Фибоначчи и высокой вероятности завершения коррекции.
 * Применяется для бэктестинга и онлайн-торговли. Защищено от заглядывания в будущее (Look-ahead bias).
 *
 * @author Alexander Kuziv
 * @since 16.09.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class FibSupportReboundRule implements Rule {
    private final CandleSeries series;
    private final FibonacciIndicator fibIndicator;
    private final int checkWindow = 3; // Сколько свечей назад мы проверяем касание уровня

    public FibSupportReboundRule(CandleSeries series, FibonacciIndicator fibIndicator) {
        this.series = series;
        this.fibIndicator = fibIndicator;
    }

    /**
     * Проверяет, произошел ли отскок от зоны Фибоначчи на текущем шаге.
     */
    @Override
    public boolean isSatisfied(int index) {
        if (index < checkWindow) return false;

        FibLevels fib = fibIndicator.getValue(index);
        // Если данных Фибоначчи для этой свечи еще нет (период прогрева)
        if (fib == FibLevels.EMPTY) return false;

        // Золотое сечение (0.618) и психологический уровень 50%
        double level618 = fib.lvl618(); // Золотое сечение (сильная поддержка)
        double level500 = fib.lvl500(); // Уровень 50% (граница зоны контроля)

        // Проверяем, заходила ли цена в зону между 50% и 61.8% за последние несколько свечей
        for (int i = index; i > index - checkWindow; i--) {
            double low = series.getLow(i);
            double close = series.getClose(i);

            // Если Тень свечи (Low) опускалась ниже 50%, но Тело (Close) закрылось выше 61.8%
            // Это классический ложный пробой и сильный отскок от зоны поддержки
            if (low <= level500 && close >= level618) {
                return true;
            }
        }
        return false;
    }
}

/*
Как использовать:

public void runFibonacciStrategy(String symbol, CandleSeries series) {
    // 1. Защита: для корректного расчета индикаторов нам нужна история
    // (например, Фибоначчи за 150 свечей и EMA 200 для определения тренда)
    if (series.size() < 200) return;

    // 2. Инициализируем индикаторы
    Indicator<Double> closePrice = index -> series.getClose(index);

    EmaIndicator ema9 = new EmaIndicator(9);
    EmaIndicator ema21 = new EmaIndicator(21);
    EmaIndicator ema200 = new EmaIndicator(200);
    FibonacciIndicator fib = new FibonacciIndicator(150); // Сетка Фибо строится по экстремумам за 150 свечей

    // Подготавливаем кэш (каскадный вызов расчета истории)
    ema9.prepare(series);
    ema21.prepare(series);
    ema200.prepare(series);
    fib.prepare(series);

    // 3. Создаем правила для ВХОДА (BUY)
    Rule isBullishTrend = new OverIndicatorRule(closePrice, ema200); // Глобальный тренд: цена выше EMA 200
    Rule emaCrossUp = new CrossedUpRule(ema9, ema21);                // Локальный разворот: EMA 9 пробила EMA 21 вверх

    // ИСПОЛЬЗОВАНИЕ НАШЕГО ПРАВИЛА:
    Rule fibRebound = new FibSupportReboundRule(series, fib);        // Факт отскока от зоны 0.500 - 0.618

    // Объединяем через логическое .and()
    // Входим, только если: ТРЕНД БЫЧИЙ + БЫЛ ОТСКОК ОТ ФИБО + ЕСТЬ РАЗВОРOТ СРЕДНИХ (EMA)
    Rule entryRule = isBullishTrend.and(fibRebound).and(emaCrossUp);

    // 4. Создаем правила для ВЫХОДА (SELL)
    Rule exitRule = new CrossedDownRule(ema9, ema21); // Выходим, когда краткосрочный тренд развернулся вниз

    // 5. Упаковываем в стратегию
    Strategy fibStrategy = new Strategy("Fibonacci Gold Rebound", entryRule, exitRule);

    System.out.println("=== СТАРТ БЭКТЕСТА ДЛЯ " + symbol + " ===");

    // Начинаем цикл с 200-й свечи, чтобы все индикаторы гарантированно прогрелись данными
    for (int i = 200; i < series.size(); i++) {

        if (fibStrategy.shouldEnter(i)) {
            double currentClose = series.getClose(i);
            double fib618 = fib.getValue(i).lvl618();
            System.out.printf("🎯 [BUY] Свеча №%d | Цена: %.2f | Поддержка Фибо 0.618 была на уровне: %.2f%n",
                i, currentClose, fib618);

        } else if (fibStrategy.shouldExit(i)) {
            System.out.printf("🚨 [SELL] Свеча №%d | Цена: %.2f | Краткосрочный тренд развернулся вниз.%n",
                i, series.getClose(i));
        }
    }

    System.out.println("=== БЭКТЕСТ ЗАВЕРШЕН ===");
}

*/

