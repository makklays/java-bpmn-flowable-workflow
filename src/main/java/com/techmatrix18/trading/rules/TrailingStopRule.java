package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.series.CandleSeries;

/**
 * TrailingStopRule
 * Трейлинг-стоп честно следует за каждым ценовым пиком на истории и защищает накопленную бумажную прибыль.
 *
 * Правило скользящего стоп-лосса (Trailing Stop).
 * Автоматически подтягивает защитный уровень вверх вслед за ростом цены.
 *
 * @author Alexander Kuziv
 * @since 16.09.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class TrailingStopRule implements Rule {

    private final CandleSeries series;
    private final TrailingTradeSession trailingTradeSession;
    private final double stopPercentage; // Процент стоп-лосса (например, 2.0 для 2%)

    public TrailingStopRule(CandleSeries series, TrailingTradeSession trailingTradeSession, double stopPercentage) {
        this.series = series;
        this.trailingTradeSession = trailingTradeSession;
        this.stopPercentage = stopPercentage;
    }

    @Override
    public boolean isSatisfied(int index) {
        // Если позиция не открыта (сессия null), правило не может сработать
        if (trailingTradeSession == null) return false;

        // 1. Обновляем исторический максимум текущей сделки, используя High текущей свечи
        trailingTradeSession.updateHighestPrice(series.getHigh(index));

        // 2. Рассчитываем динамический уровень стоп-лосса от максимального пика цены
        double highestPrice = trailingTradeSession.getHighestPrice();
        double trailingStopLevel = highestPrice * (1.0 - (stopPercentage / 100.0));

        // 3. Сигнал на выход срабатывает, если цена закрытия (Close) пробила стоп-лосс вниз
        double currentClose = series.getClose(index);
        return currentClose <= trailingStopLevel;
    }
}

/*
Как использовать:

// Конструкция .or(trailingStopRule) позволяет выйти из рынка досрочно, если тренд резко развернулся,
// не дожидаясь медленного сигнала от индикаторов (например, пересечения средних).

public void runStrategyWithTrailingStop(String symbol, CandleSeries series) {
    if (series.size() < 200) return;

    // Инициализируем индикаторы (например, Боллинджер)
    BollingerIndicator bollinger = new BollingerIndicator(20, 2.0);
    bollinger.prepare(series);

    Indicator<Double> closePrice = index -> series.getClose(index);
    Indicator<Double> upperLine = index -> bollinger.getValue(index).upper();
    Indicator<Double> middleLine = index -> bollinger.getValue(index).middle();

    // Базовые правила
    Rule entryRule = new CrossedUpRule(closePrice, upperLine);
    Rule technicalExitRule = new CrossedDownRule(closePrice, middleLine);

    // Ссылки для управления состоянием сделки в цикле
    // Изначально мы вне рынка, сессии нет
    TrailingTradeSession[] activeSessionHolder = new TrailingTradeSession[1];

    // Создаем динамическое правило скользящего стопа (2.5%)
    // Передаем массив-холдер, чтобы правило всегда видело актуальную сессию
    Rule trailingStopRule = new TrailingStopRule(series, null, 2.5) {
        @Override
        public boolean isSatisfied(int index) {
            // Переопределяем логику, чтобы динамически подхватывать сессию из холдера в цикле
            TrailingTradeSession currentSession = activeSessionHolder[0];
            if (currentSession == null) return false;
            currentSession.updateHighestPrice(series.getHigh(index));
            double trailingStopLevel = currentSession.getHighestPrice() * (1.0 - (2.5 / 100.0));
            return series.getClose(index) <= trailingStopLevel;
        }
    };

    // Финальное комбинированное правило выхода: технический сигнал ИЛИ Трейлинг-Стоп
    Rule finalExitRule = technicalExitRule.or(trailingStopRule);

    Strategy myStrategy = new Strategy("BB Breakout + Trailing Stop", entryRule, finalExitRule, 2.5);

    System.out.println("=== СТАРТ ТЕСТА С ТРЕЙЛИНГ-СТОПОМ ===");

    for (int i = 200; i < series.size(); i++) {

        // Сценарий 1: Мы вне рынка, ищем точку входа
        if (activeSessionHolder[0] == null) {
            if (myStrategy.shouldEnter(i)) {
                double price = series.getClose(i);
                // Открываем виртуальную сделку
                activeSessionHolder[0] = new TrailingTradeSession(price);
                System.out.printf("🎯 [ВХОД BUY] Свеча №%d | Цена: %.2f%n", i, price);
            }
        }
        // Сценарий 2: Мы в рынке, проверяем условия выхода (включая скользящий стоп)
        else {
            if (myStrategy.shouldExit(i)) {
                double price = series.getClose(i);
                double entryPrice = activeSessionHolder[0].getEntryPrice();
                double profitPercent = ((price - entryPrice) / entryPrice) * 100.0;

                System.out.printf("🚨 [ВЫХОД SELL] Свеча №%d | Цена: %.2f | Результат: %.2f%%%n",
                        i, price, profitPercent);

                // Закрываем сделку, очищаем сессию
                activeSessionHolder[0] = null;
            }
        }
    }
}

*/

