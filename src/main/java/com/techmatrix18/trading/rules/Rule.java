package com.techmatrix18.trading.rules;

/**
 * Rule interface defines a contract for trading rules that can be evaluated against a list of candles.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public interface Rule {

    // Check if the rule is satisfied for the candle at the given index.
    boolean isSatisfied(int index);

    default Rule and(Rule other) {
        return index -> this.isSatisfied(index) && other.isSatisfied(index);
    }

    default Rule or(Rule other) {
        return index -> this.isSatisfied(index) || other.isSatisfied(index);
    }

    default Rule not() {
        return index -> !this.isSatisfied(index);
    }
}

