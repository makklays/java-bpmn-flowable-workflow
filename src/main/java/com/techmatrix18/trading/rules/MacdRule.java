package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.MacdIndicator;
import com.techmatrix18.trading.indicators.MacdValue;

/**
 * MacdRule is a placeholder for a rule that would use the MACD (Moving Average Convergence Divergence) indicator
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class MacdRule implements Rule {
    public enum MacdCondition {
        ABOVE_ZERO,
        CROSS_UP,
        CROSS_DOWN,
        MACD_ABOVE_ZERO,
        MACD_BELOW_ZERO,
        HIST_FALLING, // Гистограмма падает (Текущая < Предыдущей) — медвежий знак
        HIST_RISING   // Гистограмма растет (Текущая > Предыдущей) — бычий знак
    }

    private final MacdIndicator macd;
    private final MacdCondition condition;

    public MacdRule(MacdIndicator macd, MacdCondition condition) {
        this.macd = macd;
        this.condition = condition;
    }

    @Override
    public boolean isSatisfied(int i) {
        // Защита: для пересечений и сравнений с историей нужно минимум 2 точки
        if (i < 1) return false;

        MacdValue current = macd.getValue(i);
        MacdValue prev = macd.getValue(i - 1);

        // Безопасная проверка (если индикатор вернул null или EMPTY на этапе прогрева)
        if (current == null || prev == null || current == MacdValue.EMPTY) {
            return false;
        }

        return switch (condition) {
            case ABOVE_ZERO -> current.histogram() > 0;
            case MACD_ABOVE_ZERO -> current.macdLine() > 0;
            case MACD_BELOW_ZERO -> current.macdLine() < 0;
            case CROSS_UP -> prev.histogram() <= 0 && current.histogram() > 0;
            case CROSS_DOWN -> prev.histogram() >= 0 && current.histogram() < 0;
            case HIST_FALLING -> current.histogram() < prev.histogram();
            case HIST_RISING -> current.histogram() > prev.histogram();
        };
    }
}

/*
Как использовать:

// 1. Инициализируем индикатор MACD с классическими параметрами (12, 26, 9)
MacdIndicator macdIndicator = new MacdIndicator(12, 26, 9);

// 2. Подготавливаем данные (считаем кэш для всей истории перед запуском правил)
macdIndicator.prepare(series);

// 3. Создаем правила на основе перечисления MacdCondition
Rule entryRule = new MacdRule(macdIndicator, MacdRule.MacdCondition.CROSS_UP);
Rule exitRule = new MacdRule(macdIndicator, MacdRule.MacdCondition.CROSS_DOWN);

// 4. Упаковываем правила в стратегию
Strategy macdStrategy = new Strategy("MACD Histogram Cross", entryRule, exitRule);

// Вход: MACD пересекает 0 вверх И RSI при этом ниже 40 (рынок перепродан, отличная точка входа)
Rule entryRule = new MacdRule(macdIndicator, MacdRule.MacdCondition.CROSS_UP)
        .and(new UnderIndicatorRule(rsiIndicator, 40.0));

// Выход: MACD пересекает 0 вниз ИЛИ цена падает ниже скользящей средней
Rule exitRule = new MacdRule(macdIndicator, MacdRule.MacdCondition.CROSS_DOWN)
        .or(new CrossedDownRule(priceIndicator, maIndicator));

Strategy complexStrategy = new Strategy("MACD + RSI Combo", entryRule, exitRule);

*/

