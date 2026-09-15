package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.series.CandleSeries;

/**
 * PricePercentRule checks if the current price has changed by a certain percentage from the entry price.
 * Стоп-Лосс и Тейк-Профит (PricePercentRule)
 * Полезно для выхода из сделки, если цена изменилась на X процентов от определенного уровня.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class PricePercentRule implements Rule {
    private final CandleSeries series; // Переходим на интерфейс
    private final double entryPrice;
    private final double percentage;
    private final boolean isProfit; // true для TakeProfit, false для StopLoss

    public PricePercentRule(CandleSeries series, double entryPrice, double percentage, boolean isProfit) {
        this.series = series;
        this.entryPrice = entryPrice;
        this.percentage = percentage;
        this.isProfit = isProfit;
    }

    @Override
    public boolean isSatisfied(int i) {
        if (i < 0 || i >= series.size()) return false;

        // Используем метод интерфейса для получения цены
        double currentPrice = series.getClose(i);
        double change = (currentPrice - entryPrice) / entryPrice * 100;

        if (isProfit) {
            // Тейк-профит: рост цены выше порога
            return change >= percentage;
        } else {
            // Стоп-лосс: падение цены ниже порога (сравнение с отрицательным значением)
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

*/

