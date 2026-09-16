package com.techmatrix18.trading.rules;

import com.techmatrix18.trading.indicators.SupportResistanceIndicator;
import com.techmatrix18.trading.indicators.SupportResistanceIndicator.Level;
import com.techmatrix18.trading.series.CandleSeries;

import java.util.List;

/**
 * Правило близости цены к уровням поддержки и сопротивления (Price Near Support/Resistance Rule).
 *
 * Проверяет, находится ли текущая цена закрытия достаточно близко к какому-либо
 * из исторически подтвержденных горизонтальных уровней фрактального индикатора.
 *
 * @author Alexander Kuziv
 * @since 16.09.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class PriceNearSupportRule implements Rule {

    private final CandleSeries series;
    private final SupportResistanceIndicator srIndicator;
    private final double sensitivityPercent; // Допустимое отклонение от уровня (например, 0.5 для 0.5%)

    /**
     * Конструктор правила.
     *
     * @param series             универсальная серия свечей
     * @param srIndicator        подготовленный индикатор уровней поддержки/сопротивления
     * @param sensitivityPercent процентный коридор близости (0.5 = 0.5%, 1.0 = 1%)
     */
    public PriceNearSupportRule(CandleSeries series, SupportResistanceIndicator srIndicator, double sensitivityPercent) {
        this.series = series;
        this.srIndicator = srIndicator;
        this.sensitivityPercent = sensitivityPercent;
    }

    @Override
    public boolean isSatisfied(int index) {
        // Получаем список всех подтвержденных уровней, известных на момент текущей свечи
        List<Level> activeLevels = srIndicator.getValue(index);

        // Если фракталы еще не успели сформировать ни одного уровня (этап прогрева), сигнал ложный
        if (activeLevels == null || activeLevels.isEmpty()) {
            return false;
        }

        // Текущая цена закрытия, которую мы проверяем на близость к уровням
        double currentPrice = series.getClose(index);

        // Итерируемся по уровням в поиске хотя бы одного совпадения
        for (Level level : activeLevels) {
            double lvlPrice = level.getPrice();

            // Вычисляем относительное процентное отклонение цены от уровня
            double deviation = Math.abs(currentPrice - lvlPrice) / lvlPrice * 100.0;

            // Если цена вошла в наш ценовой коридор — правило выполнено
            if (deviation <= sensitivityPercent) {
                return true;
            }
        }

        // Если ни один уровень не оказался достаточно близко
        return false;
    }
}

/*
Как использовать:

// В трейдинге отскок от фрактальных уровней идеально работает, когда осцилляторы (например, Стохастик) подтверждают,
// что рынок перепродан, и цена начинает разворачиваться вверх прямо у сильной горизонтальной поддержки.
// Вот пример:
public void runFractalSupportStrategy(String symbol, CandleSeries series) {
    if (series.size() < 100) return;

    // 1. Инициализируем индикаторы
    SupportResistanceIndicator srIndicator = new SupportResistanceIndicator(0.5); // шаг слияния уровней 0.5%
    StochasticIndicator stoch = new StochasticIndicator(14);

    // Расчет кэша
    srIndicator.prepare(series);
    stoch.prepare(series);

    // Выносим линии Стохастика
    Indicator<Double> kLine = index -> stoch.getValue(index).k();
    Indicator<Double> dLine = index -> stoch.getValue(index).d();

    // 2. Строим правила для ВХОДА (BUY)
    // Условие А: Цена находится в коридоре 0.4% от сильного фрактального уровня
    Rule nearSupport = new PriceNearSupportRule(series, srIndicator, 0.4);

    // Условие Б: Стохастик дает бычий сигнал (пересечение вверх в зоне перепроданности < 20)
    Rule stochSignal = new CrossedUpRule(kLine, dLine).and(new UnderIndicatorRule(kLine, 20.0));

    // Сигнал истинен, если мы стоим НА УРОВНЕ + СТОХАСТИК РАЗВЕРНУЛСЯ ВВЕРХ
    Rule entryRule = nearSupport.and(stochSignal);

    // 3. Строим правила для ВЫХОДА (SELL)
    Rule exitRule = new CrossedDownRule(kLine, dLine).and(new OverIndicatorRule(kLine, 80.0));

    // Упаковываем в стратегию
    Strategy levelStrategy = new Strategy("Fractal Support + Stochastic", entryRule, exitRule, 1.5);

    System.out.println("=== СТАРТ БЭКТЕСТА ФРАКТАЛЬНЫХ УРОВНЕЙ ===");

    for (int i = 30; i < series.size(); i++) {
        if (levelStrategy.shouldEnter(i)) {
            System.out.printf("🎯 [BUY] Свеча №%d | Вход от уровня! Цена: %.2f%n", i, series.getClose(i));
        } else if (levelStrategy.shouldExit(i)) {
            System.out.printf("🚨 [SELL] Свеча №%d | Выход по перекупленности. Цена: %.2f%n", i, series.getClose(i));
        }
    }
}

*/

