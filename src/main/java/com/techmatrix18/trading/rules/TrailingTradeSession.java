package com.techmatrix18.trading.rules;

/**
 * Хранит информацию о текущей открытой позиции для расчета рисков.
 *
 * @author Alexander Kuziv
 * @since 16.09.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class TrailingTradeSession {
    private final double entryPrice;   // Цена, по которой мы вошли в лонг
    private double highestPrice;       // Максимальная цена (High) за время удержания позиции

    public TrailingTradeSession(double entryPrice) {
        this.entryPrice = entryPrice;
        this.highestPrice = entryPrice;
    }

    /**
     * Обновляет пиковую цену на основе новой свечи.
     */
    public void updateHighestPrice(double currentHigh) {
        if (currentHigh > this.highestPrice) {
            this.highestPrice = currentHigh;
        }
    }

    public double getEntryPrice() { return entryPrice; }
    public double getHighestPrice() { return highestPrice; }
}

