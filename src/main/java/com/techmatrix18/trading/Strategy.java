package com.techmatrix18.trading;

import com.techmatrix18.trading.rules.Rule;

/**
 * Strategy class is a placeholder for implementing trading strategies.
 *
 * @author Alexander Kuziv
 * @since 15.09.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class Strategy {
    private final String name;
    private final Rule entryRule;
    private final Rule exitRule;

    public Strategy() {
        this.name = "Default Strategy";
        this.entryRule = null;
        this.exitRule = null;
    }

    // Конструктор на 2 параметра (автоматически вызывает основной конструктор)
    public Strategy(Rule entryRule, Rule exitRule) {
        this("Unnamed Strategy", entryRule, exitRule);
    }

    public Strategy(String name, Rule entryRule, Rule exitRule) {
        this.name = name;
        this.entryRule = entryRule;
        this.exitRule = exitRule;
    }

    /**
     * Проверяет сигнал на ВХОД (BUY) на текущем индексе.
     */
    public boolean shouldEnter(int index) {
        return entryRule.isSatisfied(index);
    }

    /**
     * Проверяет сигнал на ВЫХОД (SELL) на текущем индексе.
     */
    public boolean shouldExit(int index) {
        return exitRule.isSatisfied(index);
    }

    // Геттеры для движка и логирования
    public String getName() {
        return name;
    }
}

