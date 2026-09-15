package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.FibonacciIndicator;
import com.techmatrix18.trading.series.CandleSeries;

import java.util.Map;

/**
 * PriceNearFibRule checks if the current price is near a specific Fibonacci level.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class PriceNearFibRule implements Rule {
    private final CandleSeries series; // Заменили List на наш универсальный интерфейс
    private final FibonacciIndicator fib;
    private final String targetLevel;
    private final double sensitivity; // Вынесли чувствительность в параметры

    public PriceNearFibRule(CandleSeries series, FibonacciIndicator fib, String targetLevel, double sensitivity) {
        this.series = series;
        this.fib = fib;
        this.targetLevel = targetLevel;
        this.sensitivity = sensitivity;
    }

    @Override
    public boolean isSatisfied(int i) {
        Map<String, Double> levels = fib.getValue(i);
        if (levels == null || !levels.containsKey(targetLevel)) return false;

        // Используем методы нашей серии
        double currentPrice = series.getClose(i);
        double fibPrice = levels.get(targetLevel);

        // Проверка близости к уровню
        return Math.abs(currentPrice - fibPrice) / fibPrice <= sensitivity;
    }
}

/*
Как использовать:

1. Базовый пример: Касание «Золотого сечения»
Самый популярный сценарий — когда цена откатывается к уровню 0.618 и мы ждем отскока.

// Инициализация правила для уровня 61.8%
Rule nearGoldLevel = new PriceNearFibRule(fib, "level_618");
if (nearGoldLevel.isSatisfied(candles)) {
    // Цена вплотную подошла к уровню 0.618.
    // Обычно здесь трейдеры ищут подтверждение разворота.
}

2. Сложное условие: Вход при подтверждении (Fib + RSI)
Одного касания уровня часто мало. Вы можете объединить это правило с RSI, чтобы купить, когда цена у уровня Фибо И рынок перепродан:

// 1. Цена подошла к уровню 0.786 (глубокая коррекция)
Rule atDeepSupport = new PriceNearFibRule(fib, "level_786");
// 2. RSI при этом ниже 30
Rule rsiLow = new UnderIndicatorRule(rsi, 30.0);
// Комбинируем: Покупаем только если оба условия верны
Rule fibBuyStrategy = atDeepSupport.and(rsiLow);
if (fibBuyStrategy.isSatisfied(candles)) {
    telegramService.sendMessageForAll("🎯 Идеальная точка входа: Касание Фибо 0.786 при низком RSI!");
}

3. Использование для фиксации прибыли (Take Profit)
Вы можете использовать уровни Фибоначчи как цели для продажи. Например, если вы купили актив и ждете, когда он дойдет до уровня 0.236 сверху:
// Правило для фиксации прибыли у верхнего сопротивления
Rule takeProfitAtFib = new PriceNearFibRule(fib, "level_236");
if (takeProfitAtFib.isSatisfied(candles)) {
    telegramService.sendMessageForAll("💰 Пора фиксировать прибыль! Цена достигла уровня Фибо 0.236.");
}

4. Нюанс по точности (0.1%)
В вашем классе прописано: Math.abs(currentPrice - levelPrice) / levelPrice < 0.001.
    Это означает 0.1% отклонения.
    Для Bitcoin (цена ~60,000) это зазор в $60.
    Если вы торгуете на очень волатильных монетах (шиткоинах), этот зазор в конструкторе можно сделать настраиваемым, чтобы ловить более широкие касания.

*/

