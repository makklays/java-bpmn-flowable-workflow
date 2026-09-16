package com.techmatrix18.trading.indicators;

import com.techmatrix18.trading.series.CandleSeries;
import java.util.*;

/**
 * VolumeProfileIndicator is a technical analysis tool that displays trading activity over a specified time period at specified price levels.
 * Volume Profile - рассчитает распределение объема по ценовым уровням.
 * POC (Point of Control) - ценовой уровень с наибольшим объемом торгов, который может служить важным уровнем поддержки или сопротивления.
 *
 * Volume Profile помогает трейдерам понять, на каких ценовых уровнях был сосредоточен объем торгов, что может указывать на важные уровни поддержки и сопротивления.
 *
 * Индикатор Горизонтального Профиля Объемов (Volume Profile)</b>.
 * Находит уровень максимального проторгованного объема (POC — Point of Control) на скользящем окне.
 * Оптимизирован для бэктестинга O(1) и полностью избавлен от аллокации объектов в циклах.
 *
 * Индикатор VolumeProfileIndicator определяет уровень максимального проторгованного объема (POC) в качестве динамической
 * поддержки или сопротивления, а правило PriceNearPOCRule используется для поиска точек отскока при приближении цены к
 * этому уровню. Данная связка применяется в бэктестинге для реализации стратегий входа по тренду при подтверждении
 * сигнала от дополнительных индикаторов, таких как Стохастик.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

public class VolumeProfileIndicator implements Indicator<Double> {
    private final int lookbackPeriod;
    private final int binCount;

    // ИСПРАВЛЕНО: Указаны квадратные скобки и начальный размер 0
    private double[] historyCache = new double[0];

    public VolumeProfileIndicator(int lookbackPeriod, int binCount) {
        this.lookbackPeriod = lookbackPeriod;
        this.binCount = binCount;
    }

    public void prepare(CandleSeries series) {
        if (series == null || series.size() == 0) {
            historyCache = new double[0];
            return;
        }

        int size = series.size();
        historyCache = new double[size];

        // Буфер для профиля объемов, переиспользуем его, чтобы не создавать массивы в цикле
        double[] profile = new double[binCount];

        for (int i = 0; i < size; i++) {
            if (i < lookbackPeriod - 1) {
                historyCache[i] = 0.0; // Период прогрева
                continue;
            }

            int start = i - lookbackPeriod + 1;

            // 1. Быстрый поиск экстремумов цены в окне
            double minPrice = series.getLow(start);
            double maxPrice = series.getHigh(start);
            for (int j = start + 1; j <= i; j++) {
                double low = series.getLow(j);
                double high = series.getHigh(j);
                if (low < minPrice) minPrice = low;
                if (high > maxPrice) maxPrice = high;
            }

            double range = maxPrice - minPrice;
            if (range <= 0) {
                historyCache[i] = 0.0;
                continue;
            }
            double step = range / binCount;

            // Очищаем буфер профиля перед новым расчетом bar-окна
            Arrays.fill(profile, 0.0);

            // 2. Распределяем объемы по корзинам (без создания объектов)
            for (int j = start; j <= i; j++) {
                double price = series.getClose(j);
                double volume = series.getCandle(j).getVolume().doubleValue();

                int binIndex = (int) ((price - minPrice) / step);
                if (binIndex >= binCount) binIndex = binCount - 1;
                if (binIndex < 0) binIndex = 0;

                profile[binIndex] += volume;
            }

            // 3. Линейный поиск корзины с максимальным объемом (POC) без использования Stream API
            int maxBinIndex = 0;
            double maxVolume = profile[0];
            for (int k = 1; k < binCount; k++) {
                if (profile[k] > maxVolume) {
                    maxVolume = profile[k];
                    maxBinIndex = k;
                }
            }

            // Рассчитываем точную цену центра этой корзины
            double pocPrice = minPrice + (maxBinIndex * step) + (step / 2.0);
            historyCache[i] = pocPrice;
        }
    }

    @Override
    public Double getValue(int index) {
        if (index < 0 || index >= historyCache.length) {
            return 0.0;
        }
        return historyCache[index];
    }

    @Override
    public Double calculate(CandleSeries series, int index) {
        if (historyCache.length <= index) {
            prepare(series);
        }
        return getValue(index);
    }
}

/*
Как использовать:

1. Поиск Point of Control (POC) — уровня максимального объема
// Находим уровень максимального объема за последние 200 свечей, разделив диапазон на 50 уровней
double pocLevel = volumeProfileIndicator.findPOC(candles, 50);
// Пример использования: цена пробила уровень максимального объема вверх
Rule priceAbovePOC = new OverIndicatorRule((c) -> volumeProfileIndicator.findPOC(c, 50), 0.0);

2. Определение зон поддержки и сопротивления (PriceNearPOCRule)
// Создаем правило: цена находится в зоне POC (±0.2% от уровня)
Rule atPOCZone = new PriceNearPOCRule(volumeProfileIndicator, 50);
if (atPOCZone.isSatisfied(candles)) {
    telegramService.sendMessageForAll("📊 Цена подошла к уровню максимального объема (POC). Ожидаем реакцию рынка!");
}

3. Комбинирование с осцилляторами (Подтвержденный отскок)
// Покупаем, если цена у POC И RSI < 30
Rule buyAtValueArea = new PriceNearPOCRule(volumeProfileIndicator, 50)
                      .and(new UnderIndicatorRule(rsiIndicator, 30.0));
if (buyAtValueArea.isSatisfied(candles)) {
    telegramService.sendMessageForAll("🎯 СИГНАЛ: Отскок от объема подтвержден RSI!");
}

4. Подготовка данных для фронтенда
@GetMapping("/volume-profile")
public List<VolumeProfileIndicator.VolumeBin> getProfile(@RequestParam String symbol) {
    List<Candle> candles = repository.findTop200BySymbolOrderByTimestampDesc(symbol);
    // Возвращаем 30 горизонтальных корзин объема для отрисовки гистограммы
    return volumeProfileIndicator.calculateProfile(candles, 30);
}

// Логика торговой стратегии (Volume Bounce)
// 1. Глобальный тренд — бычий: Цена находится выше тяжелой EMA 200.
// 2. Вход (BUY): Цена скорректировалась (упала) к самому сильному объёмному уровню POC за последние 100 свечей и
//    находится около него в пределах 0.5% (PriceNearPOCRule). В этот же момент Стохастик подтверждает разворот,
//    пересекаясь снизу вверх (CrossedUpRule) в зоне перепроданности.
// 3. Выход (SELL): Локальный тренд разворачивается (быстрая EMA 9 пересекает EMA 21 сверху вниз).
public class VolumeProfileStrategyExample {

    public void runVolumeProfileStrategy(String symbol, CandleSeries series) {
        // 1. Защита: для прогрева EMA 200 и профиля объемов нужно минимум 200 свечей
        if (series.size() < 200) return;

        // 2. Инициализируем индикаторы
        Indicator<Double> closePrice = index -> series.getClose(index);

        EmaIndicator ema9 = new EmaIndicator(9);
        EmaIndicator ema21 = new EmaIndicator(21);
        EmaIndicator ema200 = new EmaIndicator(200);

        // Профиль объемов за последние 100 свечей, разбитый на 24 ценовые корзины
        VolumeProfileIndicator vpi = new VolumeProfileIndicator(100, 24);
        StochasticIndicator stoch = new StochasticIndicator(14);

        // 3. Рассчитываем кэш истории (вызывается один раз перед бэктестом)
        ema9.prepare(series);
        ema21.prepare(series);
        ema200.prepare(series);
        vpi.prepare(series);
        stoch.prepare(series);

        // Выделяем линии Стохастика (%K и %D) как независимые индикаторы через лямбды
        Indicator<Double> kLine = index -> stoch.getValue(index).k();
        Indicator<Double> dLine = index -> stoch.getValue(index).d();

        // 4. Строим логические ПРАВИЛА
        // Условие А: Глобальный тренд восходящий (Цена выше EMA 200)
        Rule isBullishTrend = new OverIndicatorRule(closePrice, ema200);

        // Условие Б: Цена находится в коридоре 0.5% от уровня максимального объема POC
        Rule priceNearVolume = new PriceNearPOCRule(series, vpi, 0.5);

        // Условие В: Стохастик дает бычий сигнал в зоне перепроданности (< 20)
        Rule stochSignal = new CrossedUpRule(kLine, dLine).and(new UnderIndicatorRule(kLine, 20.0));

        // Финальное правило ВХОДА (BUY): ТРЕНД + РЯДОМ КРУПНЫЙ ОБЪЕМ POC + СТОХАСТИК РАЗВЕРНУЛСЯ
        Rule entryRule = isBullishTrend.and(priceNearVolume).and(stochSignal);

        // Финальное правило ВЫХОДА (SELL): Быстрая EMA 9 пересекает медленную EMA 21 вниз
        Rule exitRule = new CrossedDownRule(ema9, ema21);

        // 5. Упаковываем в стратегию
        Strategy vpStrategy = new Strategy("Volume Profile + Stochastic Rebound", entryRule, exitRule, 1.5);

        System.out.println("=== СТАРТ БЭКТЕСТА ПРОФИЛЯ ОБЪЕМОВ ДЛЯ " + symbol + " ===");

        // Начинаем с 200 свечи, когда все индикаторы гарантированно накопили историю
        for (int i = 200; i < series.size(); i++) {

            if (vpStrategy.shouldEnter(i)) {
                double currentClose = series.getClose(i);
                double currentPoc = vpi.getValue(i);
                System.out.printf("🎯 [BUY] Свеча №%d | Вход от сильного объема! Цена: %.2f | Уровень POC: %.2f%n",
                        i, currentClose, currentPoc);

            } else if (vpStrategy.shouldExit(i)) {
                System.out.printf("🚨 [SELL] Свеча №%d | Краткосрочный тренд развернулся вниз. Выход по цене: %.2f%n",
                        i, series.getClose(i));
            }
        }

        System.out.println("=== БЭКТЕСТ ЗАВЕРШЕН ===");
    }
}

*/

