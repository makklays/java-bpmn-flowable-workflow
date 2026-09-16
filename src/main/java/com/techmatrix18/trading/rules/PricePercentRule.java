package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.series.CandleSeries;

/**
 * PricePercentRule checks if the current price has changed by a certain percentage from the entry price.
 * Стоп-Лосс и Тейк-Профит (PricePercentRule)
 * Полезно для выхода из сделки, если цена изменилась на X процентов от определенного уровня.
 *
 * Правило фиксированного процента (Take Profit / Stop Loss)
 * Честно проверяет выход цены за рамки ограничений, используя экстремумы свечей (High/Low),
 * а не только цену закрытия (Close), что исключает погрешности.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class PricePercentRule implements Rule {
    private final CandleSeries series;
    private final TrailingTradeSession tradeSession; // Берём цену входа динамически из сессии
    private final double percentage;
    private final boolean isProfit; // true для TakeProfit, false для StopLoss

    /**
     * Конструктор правила.
     *
     * @param series       универсальная серия свечей
     * @param tradeSession ссылка на объект текущей сделки движка
     * @param percentage   процент изменения (например, 2.0 для 2%)
     * @param isProfit     true — это Тейк-Профит, false — это Стоп-Лосс
     */
    public PricePercentRule(CandleSeries series, TrailingTradeSession tradeSession, double percentage, boolean isProfit) {
        this.series = series;
        this.tradeSession = tradeSession;
        this.percentage = percentage;
        this.isProfit = isProfit;
    }

    @Override
    public boolean isSatisfied(int i) {
        // Если мы вне рынка (сессии сделки нет), правила рисков не должны срабатывать
        if (tradeSession == null) return false;

        double entryPrice = tradeSession.getEntryPrice();

        if (isProfit) {
            // Тейк-профит для лонга: проверяем, долетала ли максимальная цена (High) до цели
            double maxPriceInCandle = series.getHigh(i);
            double change = (maxPriceInCandle - entryPrice) / entryPrice * 100.0;
            return change >= percentage;
        } else {
            // Стоп-лосс для лонга: проверяем, опускалась ли минимальная цена (Low) ниже порога
            double minPriceInCandle = series.getLow(i);
            double change = (minPriceInCandle - entryPrice) / entryPrice * 100.0;
            return change <= -percentage;
        }
    }
}

/*
Как использовать:

// Представим, что мы купили BTC по 60,000
double entryPrice = 60000.0;
// Стоп-лосс: цена упала на 2% от входа
Rule stopLoss = new PricePercentRule(entryPrice, 2.0, false);
// Тейк-профит: цена выросла на 5% от входа
Rule takeProfit = new PricePercentRule(entryPrice, 5.0, true);

//
public void runBbStrategyWithRiskManagement(String symbol, CandleSeries series) {
    if (series.size() < 200) return;

    // 1. Инициализируем индикаторы
    BollingerIndicator bb = new BollingerIndicator(20, 2.0);
    bb.prepare(series);

    Indicator<Double> closePrice = index -> series.getClose(index);
    Indicator<Double> middleLine = index -> bb.getValue(index).middle();

    // 2. Базовые правила
    Rule entryRule = new CrossedUpRule(closePrice, index -> bb.getValue(index).upper());
    Rule technicalExit = new CrossedDownRule(closePrice, middleLine); // Выход, если упали ниже средней линии

    // Холдер для динамического отслеживания сделки в цикле
    TradeSession[] activeSession = new TradeSession;

    // 3. Создаем правила риск-менеджмента "на лету" через анонимные адаптеры, чтобы они видели activeSession
    Rule takeProfitRule = index -> {
        if (activeSession == null) return false;
        double change = (series.getHigh(index) - activeSession.getEntryPrice()) / activeSession.getEntryPrice() * 100.0;
        return change >= 3.0; // 3% Тейк-Профит
    };

    Rule stopLossRule = index -> {
        if (activeSession == null) return false;
        double change = (series.getLow(index) - activeSession.getEntryPrice()) / activeSession.getEntryPrice() * 100.0;
        return change <= -1.5; // 1.5% Стоп-Лосс
    };

    // Комбинируем все варианты выхода: Индикатор ИЛИ Тейк-Профит ИЛИ Стоп-Лосс
    Rule finalExitRule = technicalExit.or(takeProfitRule).or(stopLossRule);

    Strategy myStrategy = new Strategy("BB Breakout + Fixed Risk", entryRule, finalExitRule, 1.5);

    // 4. Цикл симулятора
    for (int i = 200; i < series.size(); i++) {
        if (activeSession == null) {
            if (myStrategy.shouldEnter(i)) {
                activeSession = new TradeSession(series.getClose(i));
                System.out.printf("🎯 [ВХОД BUY] Свеча №%d | Цена: %.2f%n", i, series.getClose(i));
            }
        } else {
            if (myStrategy.shouldExit(i)) {
                double exitPrice = series.getClose(i);

                // Проверяем, по какой именно причине мы вышли, для красивого лога
                if (takeProfitRule.isSatisfied(i)) {
                    System.out.printf("💰 [ВЫХОД TAKE PROFIT] Свеча №%d | Сняли профит!%n", i);
                } else if (stopLossRule.isSatisfied(i)) {
                    System.out.printf("💸 [ВЫХОД STOP LOSS] Свеча №%d | Сработал стоп.%n", i);
                } else {
                    System.out.printf("🚨 [ВЫХОД ТЕХНИЧЕСКИЙ] Свеча №%d | Индикатор развернулся. Цена: %.2f%n", i, exitPrice);
                }

                activeSession = null; // Закрываем позицию
            }
        }
    }
}

*/

