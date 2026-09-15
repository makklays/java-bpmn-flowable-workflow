package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.MacdIndicator;

import java.util.Map;

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
        ABOVE_ZERO, CROSS_UP, CROSS_DOWN, MACD_ABOVE_ZERO, MACD_BELOW_ZERO, HIST_DECREASING
    }

    private final MacdIndicator macd;
    private final MacdCondition condition;

    public MacdRule(MacdIndicator macd, MacdCondition condition) {
        this.macd = macd;
        this.condition = condition;
    }

    @Override
    public boolean isSatisfied(int i) {
        // Для условий пересечения и затухания нужно минимум 2 точки
        if (i < 1) return false;

        Map<String, Double> current = macd.getValue(i);
        Map<String, Double> prev = macd.getValue(i - 1);

        if (current.isEmpty()) return false;

        double currentHist = current.getOrDefault("histogram", 0.0);
        double currentLine = current.getOrDefault("macdLine", 0.0);
        double prevHist = prev.getOrDefault("histogram", 0.0);

        return switch (condition) {
            case ABOVE_ZERO -> currentHist > 0;
            case MACD_ABOVE_ZERO -> currentLine > 0;
            case MACD_BELOW_ZERO -> currentLine < 0;
            case CROSS_UP -> prevHist <= 0 && currentHist > 0;
            case CROSS_DOWN -> prevHist >= 0 && currentHist < 0;
            case HIST_DECREASING -> Math.abs(currentHist) < Math.abs(prevHist);
            default -> false;
        };
    }
}

/*
Как использовать:

1. Глобальный тренд вверх (Линия MACD > 0)
// Использование в коде:
Rule globalUptrend = new MacdRule(macd, MacdRule.MacdCondition.MACD_ABOVE_ZERO);
if (globalUptrend.isSatisfied(candles)) {
    // Мы в зоне бычьего тренда, можно рассматривать покупки
}

2. Затухание тренда (Гистограмма падает)
Это «ранний звоночек». Цена еще может расти, но столбики гистограммы уже становятся меньше — значит, сила покупателей иссякает.
// Проверка: текущая гистограмма меньше предыдущей
Rule momentumFading = new MacdRule(macd, MacdRule.MacdCondition.HIST_DECREASING);
if (momentumFading.isSatisfied(candles)) {
    telegramService.sendMessageForAll("⚠️ Внимание: Бычий импульс затухает, возможен разрот!");
}

// 1. Покупаем, когда MACD пересек сигнальную линию вверх
Rule buySignal = new MacdRule(macd, MacdRule.MacdCondition.CROSS_UP);

// 2. Продаем, когда MACD пересек сигнальную линию вниз
Rule sellSignal = new MacdRule(macd, MacdRule.MacdCondition.CROSS_DOWN);

// 3. Комбинируем: Покупаем, если MACD пересек сигнал ВВЕРХ и при этом мы у уровня Фибо
Rule smartBuy = buySignal.and(new PriceNearFibRule(fib, "level_618"));

*/

