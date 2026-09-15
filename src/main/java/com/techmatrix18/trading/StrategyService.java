package com.techmatrix18.trading;

import com.techmatrix18.dto.BacktestDto;
import com.techmatrix18.dto.PriceLevelDto;
import com.techmatrix18.dto.SignalDto;
import com.techmatrix18.dto.TradeDto;
import com.techmatrix18.mapper.CandleMapper;
import com.techmatrix18.model.Candle;
import com.techmatrix18.telegram.TelegramService;
import com.techmatrix18.trading.indicators.*;
import com.techmatrix18.trading.rules.*;
import com.techmatrix18.trading.series.CandleSeries;
import com.techmatrix18.trading.series.HistoricalCandleSeries;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * StrategyService is responsible for managing trading strategies, including their creation, execution, and performance tracking.
 * StrategyService — это «мозг», который принимает торговые решения.
 * Использование комбинации правил Rule через .and() и .or() для создания конкретной стратегии «Вход» и «Выход», управление рисками.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */
@Service
public class StrategyService {
    // Индикаторы без параметров можно создать сразу (если они не @Component)
    private final RsiIndicator rsiIndicator = new RsiIndicator();
    private final AtrIndicator atr14 = new AtrIndicator(14);
    private final SmaIndicator sma200 = new SmaIndicator(200);
    private final EmaIndicator ema50 = new EmaIndicator(50);
    private final EmaIndicator ema200 = new EmaIndicator(200);

    private final SmaIndicator fastSma = new SmaIndicator(10);
    private final SmaIndicator slowSma = new SmaIndicator(30);

    private final StochasticIndicator stochasticIndicator = new StochasticIndicator();

    private final MacdIndicator macd = new MacdIndicator(12, 26, 9);

    // Эти требуют параметров, их создадим позже или здесь с дефолтами
    private final BollingerIndicator bollingerIndicator = new BollingerIndicator(20, 2.0, "MIDDLE");
    private final FibonacciIndicator fibonacciIndicator = new FibonacciIndicator(100);
    private final VolumeProfileIndicator volumeProfileIndicator = new VolumeProfileIndicator(200, 50);

    private final TelegramService telegramService;

    public StrategyService(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    // Соберем полноценную торговую систему.
    // Допустим, мы хотим покупать при сильном откате к Фибоначчи и продавать при перекупленности.
    public void runLogic(String symbol, CandleSeries series) {
        if (series.size() < 200) return; // Ждем накопления данных (например, для SMA200)

        // 1. ПОДГОТОВКА: Обновляем кэш индикаторов для всей текущей серии
        // В онлайн-режиме это происходит быстро, так как серия ограничена буфером
        rsiIndicator.prepare(series);
        bollingerIndicator.prepare(series);
        fibonacciIndicator.prepare(series);
        // ... подготовить остальные индикаторы

        // 2. ИНДИКАТОР ЦЕНЫ: Обертка для получения текущей цены
        Indicator<Double> closePrice = new Indicator<Double>() {
            @Override public Double getValue(int index) { return series.getClose(index); }
            @Override public Double calculate(CandleSeries s, int idx) { return s.getClose(idx); }
        };

        // 3. ТЕКУЩИЙ ИНДЕКС: Нас интересует только самая последняя закрытая свеча
        int i = series.size() - 1;

        // 4. ПРАВИЛА ВХОДА
        Rule rsiOversold = new UnderIndicatorRule(rsiIndicator, 30.0);
        Rule bbCrossUp = new CrossedUpRule(bollingerIndicator, closePrice);
        Rule entrySignal = rsiOversold.and(bbCrossUp);

        if (entrySignal.isSatisfied(i)) {
            telegramService.sendMessageForAll("✅ ВХОД " + symbol + ": RSI < 30 + Пробой Боллинджера!");
            // Здесь можно вызвать метод для создания реального ордера
        }

        // 5. ПРАВИЛА ВЫХОДА
        Rule rsiExit = new CrossedDownRule(rsiIndicator, 70.0);
        Rule fibTarget = new PriceNearFibRule(series, fibonacciIndicator, "level_236", 0.001);
        Rule exitSignal = rsiExit.or(fibTarget);

        if (exitSignal.isSatisfied(i)) {
            telegramService.sendMessageForAll("❌ ВЫХОД " + symbol + ": Цель достигнута или RSI развернулся!");
            // Здесь можно вызвать метод для закрытия позиции
        }
    }

    /**
     * 15.09.2026: Новый метод для демонстрации работы с индикаторами и правилами (для бектеста)
     */
    public void runMyLogic(String symbol, CandleSeries series) {
        // 1. Проверяем минимальный порог данных для корректного расчета SMA 200 (пропускаем первые 200 свечей для прогрева)
        if (series.size() < 200) return;

        // 2. Подготовка индикаторов (вычисляем кэш один раз для всей истории)
        rsiIndicator.prepare(series);
        fastSma.prepare(series);
        slowSma.prepare(series);

        // 3. Строим правила для Входа (BUY)
        Rule crossedUp = new IndicatorCrossedUpRule(fastSma, slowSma);
        Rule rsiLow = new UnderIndicatorRule(rsiIndicator, 50.0); // RSI < 50
        Rule entryRule = crossedUp.and(rsiLow);

        // 4. Строим правила для Выхода (SELL)
        Rule exitRule = new IndicatorCrossedUpRule(slowSma, fastSma);

        // 5. Создаем готовую стратегию
        Strategy movingAverageCrossStrategy = new Strategy("Bollinger + RSI Cross", entryRule, exitRule);

        System.out.println("=== СТАРТ БЭКТЕСТА ДЛЯ " + symbol + " ===");

        // 6. Пробегаем по истории. Начинаем с 200, так как до этого индикаторы "прогревались"
        for (int i = 200; i < series.size(); i++) {
            if (movingAverageCrossStrategy.shouldEnter(i)) {
                //telegramService.sendMessageForAll("🎯 СИГНАЛ НА ВХОД: Боллинджер пробит + RSI подтверждает!");
                // Берем цену закрытия свечи, на которой зафиксирован сигнал
                double closePrice = series.getClose(i);
                //System.out.println("Сигнал на ПОКУПКУ на свече №" + i);
                System.out.printf("🎯 СИГНАЛ НА ВХОД (BUY) | Свеча №%d | Цена: %.2f%n", i, closePrice);
            } else if (movingAverageCrossStrategy.shouldExit(i)) {
                //telegramService.sendMessageForAll("🎯 СИГНАЛ НА ВЫХОД: Боллинджер пробит + RSI подтверждает!");
                double closePrice = series.getClose(i);
                //System.out.println("Сигнал на ПРОДАЖУ на свече №" + i);
                System.out.printf("🚨 СИГНАЛ НА ВЫХОД (SELL) | Свеча №%d | Цена: %.2f%n", i, closePrice);
            }
        }

        System.out.println("=== БЭКТЕСТ ЗАВЕРШЕН ===");
    }

    // Проверим только входные сигналы для простоты - пример
    public void checkEntry(CandleSeries series) {
        // 1. ПОДГОТОВКА: Обновляем кэш индикаторов для текущей серии
        rsiIndicator.prepare(series);
        bollingerIndicator.prepare(series);

        // 2. ИНДИКАТОР ЦЕНЫ: Обертка, чтобы правило пробоя знало цену закрытия
        // Создаем индикатор цены, совместимый с интерфейсом Indicator<Double>
        Indicator<Double> closePrice = new Indicator<Double>() {
            @Override
            public Double calculate(CandleSeries s, int index) {
                return s.getClose(index);
            }

            @Override
            public Double getValue(int index) {
                // Здесь мы обращаемся к внешней переменной series
                return series.getClose(index);
            }
        };

        // 3. ТЕКУЩИЙ ИНДЕКС: Проверяем последнюю закрытую свечу
        int i = series.size() - 1;
        if (i < 1) return; // Нужно минимум 2 свечи для CrossedUpRule

        // 4. ПРАВИЛА
        // В CrossedUpRule передаем индикатор и цену (Случай 2 из реализации правила)
        Rule crossedBollinger = new CrossedUpRule(bollingerIndicator, closePrice);
        Rule rsiLow = new UnderIndicatorRule(rsiIndicator, 30.0);

        // ОБЪЕДИНЯЕМ
        Rule entryStrategy = crossedBollinger.and(rsiLow);

        // 5. ПРОВЕРКА
        if (entryStrategy.isSatisfied(i)) {
            telegramService.sendMessageForAll("🎯 СИГНАЛ НА ВХОД: Боллинджер пробит + RSI подтверждает!");
        }
    }

    // Добавим еще один пример для MACD + Фибоначчи
    public void checkEntryMACD(CandleSeries series) {
        // 1. ПОДГОТОВКА: Заполняем кэш индикаторов для текущей серии
        fibonacciIndicator.prepare(series);
        macd.prepare(series);

        // 2. ИНДЕКС: Работаем с последней закрытой свечой
        int i = series.size() - 1;
        if (i < 1) return; // Минимум данных для анализа

        // 3. ПРАВИЛА
        // Обновляем PriceNearFibRule: теперь оно принимает серию
        // 0.001 — чувствительность (0.1% от уровня)
        Rule nearFib = new PriceNearFibRule(series, fibonacciIndicator, "level_618", 0.001);

        // MacdRule уже адаптирован под i
        Rule macdPositive = new MacdRule(macd, MacdRule.MacdCondition.ABOVE_ZERO);

        // ОБЪЕДИНЯЕМ
        Rule fibMacdStrategy = nearFib.and(macdPositive);

        // 4. ПРОВЕРКА
        if (fibMacdStrategy.isSatisfied(i)) {
            telegramService.sendMessageForAll("🎯 СИГНАЛ: Отскок от Фибо + MACD подтверждает рост!");
        }
    }

    // Профессиональная стратегия: Тренд + Поддержка + Сигнал.
    // Покупаем, если тренд глобально растущий, цена откатилась к Фибо, а импульс только что подтвердился пересечением.
    public void checkProfessionalStrategy(CandleSeries series) {
        // 1. ПОДГОТОВКА: Заполняем кэш индикаторов для текущей серии
        macd.prepare(series);
        fibonacciIndicator.prepare(series);

        // 2. ИНДЕКС: Последняя закрытая свеча
        int i = series.size() - 1;
        if (i < 1) return; // Минимум данных для пересечения (CROSS_UP)

        // 3. ПРАВИЛА
        // Глобальный тренд
        Rule trendIsUp = new MacdRule(macd, MacdRule.MacdCondition.MACD_ABOVE_ZERO);

        // Цена у поддержки (передаем серию и чувствительность 0.1%)
        Rule fibSupport = new PriceNearFibRule(series, fibonacciIndicator, "level_618", 0.001);

        // Точка входа (пересечение)
        Rule entryPoint = new MacdRule(macd, MacdRule.MacdCondition.CROSS_UP);

        // 4. ОБЪЕДИНЯЕМ И ПРОВЕРЯЕМ
        Rule fullStrategy = trendIsUp.and(fibSupport).and(entryPoint);

        if (fullStrategy.isSatisfied(i)) {
            telegramService.sendMessageForAll("💎 СИГНАЛ ВЫСОКОЙ ТОЧНОСТИ: Тренд подтвержден, вход от Фибо!");
        }
    }

    // Сигнал на выход - проверим, не выдыхается ли тренд. Если RSI в зоне перекупленности И импульс MACD затухает — пора фиксировать прибыль.
    public void checkExitStrategy(CandleSeries series) {
        // 1. ПОДГОТОВКА: Заполняем кэш для корректной работы getValue(i)
        rsiIndicator.prepare(series);
        macd.prepare(series);

        // 2. ИНДЕКС: Последняя закрытая свеча
        int i = series.size() - 1;
        if (i < 1) return;

        // 3. ПРАВИЛА
        // Условие: Мы находимся в зоне перекупленности по RSI (> 70)
        Rule overbought = new OverIndicatorRule(rsiIndicator, 70.0);

        // Условие: Импульс MACD начал затухать
        Rule momentumFading = new MacdRule(macd, MacdRule.MacdCondition.HIST_DECREASING);

        // 4. ПРОВЕРКА
        if (overbought.and(momentumFading).isSatisfied(i)) {
            telegramService.sendMessageForAll("⚠️ ВНИМАНИЕ: Тренд выдыхается. Рекомендуется фиксация прибыли.");
        }
    }

    // Проверим стратегию, основанную на объеме. Если цена подошла к уровню с большим объемом (POC) И RSI подтверждает перепроданность — ждем отскок.
    // Самая эффективная тактика — искать отскок от POC при подтверждении от осциллятора (например, RSI).
    public void checkVolumeStrategy(CandleSeries series) {
        // 1. ПОДГОТОВКА: Наполняем кэш индикаторов данными из серии
        volumeProfileIndicator.prepare(series);
        rsiIndicator.prepare(series);

        // 2. ИНДЕКС: Работаем с последней закрытой свечой
        int i = series.size() - 1;
        if (i < 0) return;

        // 3. ПРАВИЛА
        // В PriceNearPOCRule теперь передаем: серию, индикатор и чувствительность (например, 0.2%)
        Rule atPOC = new PriceNearPOCRule(series, volumeProfileIndicator, 0.2);

        // RSI ниже 30
        Rule rsiLow = new UnderIndicatorRule(rsiIndicator, 30.0);

        // 4. ОБЪЕДИНЯЕМ И ПРОВЕРЯЕМ
        Rule entrySignal = atPOC.and(rsiLow);

        if (entrySignal.isSatisfied(i)) {
            telegramService.sendMessageForAll("📊 СИГНАЛ: Цена на уровне максимального объема (POC) + RSI перепродан. Ожидаем отскок!");
        }
    }

    // Этот метод демонстрирует, как можно объединить несколько правил для создания комплексного анализа.
    // Например, мы можем создать стратегию, которая требует одновременного выполнения нескольких условий
    // для входа в позицию.
    public void executeFullAnalysis(String symbol, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        // 1. Оборачиваем данные в серию
        CandleSeries series = new HistoricalCandleSeries(candles);

        // 2. ПОДГОТОВКА: Заполняем кэш всех используемых индикаторов
        macd.prepare(series);
        rsiIndicator.prepare(series);
        fibonacciIndicator.prepare(series);

        // 3. ТЕКУЩИЙ ИНДЕКС: Последняя закрытая свеча
        int i = series.size() - 1;
        if (i < 1) return; // Минимум для условий CROSS

        // 4. СТРАТЕГИЯ ВХОДА (BUY)
        // Добавляем серию и чувствительность 0.001 в PriceNearFibRule
        Rule buySignal = new MacdRule(macd, MacdRule.MacdCondition.CROSS_UP)
            .and(new UnderIndicatorRule(rsiIndicator, 40.0))
            .and(new PriceNearFibRule(series, fibonacciIndicator, "level_618", 0.001));

        if (buySignal.isSatisfied(i)) {
            telegramService.sendMessageForAll("🚀 [" + symbol + "] СИГНАЛ НА ВХОД: MACD Cross + Fib 0.618 + RSI low");
        }

        // 5. СТРАТЕГИЯ ВЫХОДА (SELL / EXIT)
        Rule sellSignal = new MacdRule(macd, MacdRule.MacdCondition.CROSS_DOWN)
            .or(new OverIndicatorRule(rsiIndicator, 70.0))
            .or(new PriceNearFibRule(series, fibonacciIndicator, "level_236", 0.001));

        if (sellSignal.isSatisfied(i)) {
            telegramService.sendMessageForAll("⚠️ [" + symbol + "] СИГНАЛ НА ВЫХОД: Тренд ослаб или достигнута цель");
        }
    }

    // Выполняет бэктест стратегии на истории: рассчитывает сигналы входа/выхода и уровни поддержки/сопротивления
    public BacktestDto analyzeHistory(List<Candle> candles, String timeframe) {
        // 1. Агрегируем свечи (1m -> выбранный таймфрейм)
        List<Candle> aggregatedList = aggregate(candles, timeframe);

        // 2. Оборачиваем в нашу универсальную серию
        CandleSeries series = new HistoricalCandleSeries(aggregatedList);

        // 3. ПОДГОТОВКА ИНДИКАТОРОВ (Важно: считаем всю историю один раз)
        // 1. Обновляем кэш индикаторов перед проверкой (если серия изменилась)
        ema50.prepare(series);
        ema200.prepare(series);
        macd.prepare(series);
        rsiIndicator.prepare(series);
        stochasticIndicator.prepare(series);
        // fibonacciIndicator.prepare(series); // Если будете использовать

        BacktestDto report = new BacktestDto();
        // Отправляем на фронтенд именно агрегированные свечи
        report.setCandles(CandleMapper.toDtoList(aggregatedList));

        // Список сигналов
        List<SignalDto> signals = new ArrayList<>();
        // Список сделок
        List<TradeDto> trades = new ArrayList<>();

        BigDecimal stopLossPercent = new BigDecimal("3.0"); // Стоп-лосс 3%

        // ПЕРЕМЕННАЯ ДЛЯ ТЕКУЩЕЙ ОТКРЫТОЙ СДЕЛКИ
        TradeDto currentTrade = null;

        for (int i = 35; i < series.size(); i++) {
            Candle currentCandle = series.getCandle(i);
            BigDecimal closePrice = currentCandle.getClose();

            // ЕСЛИ СДЕЛКА НЕ ОТКРЫТА — ИЩЕМ ВХОД
            if (currentTrade == null) {
                if (checkBuyCondition(series, i)) {
                    currentTrade = new TradeDto(currentCandle.getOpenTime(), closePrice, "BUY");

                    // РАССЧИТЫВАЕМ ЦЕНУ СТОП-ЛОССА СРАЗУ ПРИ ВХОДЕ
                    // Например: Entry Price * 0.97 (для стопа в 3%)
                    BigDecimal slFactor = BigDecimal.ONE.subtract(stopLossPercent.divide(new BigDecimal("100")));
                    currentTrade.setStopLoss(closePrice.multiply(slFactor));

                    signals.add(new SignalDto(currentCandle.getOpenTime(), currentCandle.getSymbol(),"BUY", closePrice, "Entry"));
                }
            }
            // ЕСЛИ СДЕЛКА ОТКРЫТА — ИЩЕМ ВЫХОД
            else {
                // Сравниваем цену закрытия текущей свечи с уровнем стоп-лосса
                boolean isStopLossTriggered = currentCandle.getClose().compareTo(currentTrade.getStopLoss()) <= 0;
                // Плюс проверка по индикаторам
                boolean isIndicatorExit = checkSellCondition(series, i);

                if (isIndicatorExit || isStopLossTriggered) {
                    currentTrade.exitTime = currentCandle.getOpenTime();
                    currentTrade.exitPrice = closePrice;

                    // Расчет профита/убытка
                    BigDecimal diff = currentTrade.exitPrice.subtract(currentTrade.entryPrice);
                    currentTrade.profit = diff;
                    currentTrade.profitPercent = diff.divide(currentTrade.entryPrice, 4, BigDecimal.ROUND_HALF_UP)
                            .multiply(new BigDecimal(100));

                    trades.add(currentTrade);

                    // Пометка в сигнале, по какой причине вышли
                    String exitReason = isStopLossTriggered ? "Stop Loss" : "Indicator Exit";
                    signals.add(new SignalDto(currentCandle.getOpenTime(), currentCandle.getSymbol(), "SELL", closePrice, exitReason));

                    currentTrade = null;
                }
            }
        }
        report.setSignals(signals);
        report.setTrades(trades);
        report.setTotalTrades(trades.size());

        return report;
    }

    // Обновленный метод покупки (принимает Series и Index)
    private boolean checkBuyCondition(CandleSeries series, int i) {
        if (i < 1) return false;

        // 1. если просчет индикаторов из основного цикла (чтобы не зависало и не считало по 10 000 раз одно и тоже)
        // 2. Получаем значения для конкретного индекса i
        double val50 = ema50.calculate(series, i);
        double val200 = ema200.calculate(series, i);
        double currentRsi = rsiIndicator.calculate(series, i);
        // Получаем данные Стохастика
        double kCurr = stochasticIndicator.calculate(series, i);
        double dCurr = stochasticIndicator.getDValue(i);
        double kPrev = stochasticIndicator.calculate(series, i - 1);
        double dPrev = stochasticIndicator.getDValue(i - 1);
        double price = series.getClose(i);

        // 3. Проверяем условие фильтра (Цена выше обеих EMA)
        boolean isPriceAboveEma = price > val50 && price > val200;
        boolean isRsiLow = currentRsi < 30;

        // 4. Проверяем основной сигнал (MACD)
        Rule entryPoint = new MacdRule(macd, MacdRule.MacdCondition.CROSS_UP);

        // Условие: Пересечение K и D снизу вверх в зоне < 20
        boolean stochSignal = (kPrev <= dPrev && kCurr > dCurr) && (kPrev < 30);

        // Сюда можно добавить .and(new UnderIndicatorRule(rsiIndicator, 40))
        return isRsiLow && stochSignal;  // isPriceAboveEma && entryPoint.isSatisfied(i);
    }

    // Обновленный метод продажи (принимает Series и Index)
    private boolean checkSellCondition(CandleSeries series, int i) {
        if (i < 1) return false;

        // 1. Получаем значения индикаторов для текущего индекса i
        double val50 = ema50.calculate(series, i);
        double val200 = ema200.calculate(series, i);
        double currentRsi = rsiIndicator.calculate(series, i);

        // Данные Стохастика
        double kCurr = stochasticIndicator.calculate(series, i);
        double dCurr = stochasticIndicator.getDValue(i);
        double kPrev = stochasticIndicator.calculate(series, i - 1);
        double dPrev = stochasticIndicator.getDValue(i - 1);

        double price = series.getClose(i);

        // 2. Зеркальные фильтры
        // Цена ниже обеих EMA (признак нисходящего тренда)
        boolean isPriceBelowEma = price < val50 && price < val200;

        // RSI в зоне перекупленности (выше 80, так как вход был ниже 20)
        boolean isRsiAbove = currentRsi > 70;

        // 3. Зеркальный сигнал Стохастика
        // Пересечение K и D СВЕРХУ ВНИЗ в зоне > 80
        boolean stochSellSignal = (kPrev >= dPrev && kCurr < dCurr) && (kPrev > 70);

        // 4. Зеркальный сигнал MACD (если используете)
        // Rule exitPoint = new MacdRule(macd, MacdRule.MacdCondition.CROSS_DOWN);

        // Возвращаем true, если условия для выхода (продажи) выполнены
        return isRsiAbove && stochSellSignal;
    }
    /*private boolean checkSellCondition(CandleSeries series, int i) {
        Rule sellSignal = new MacdRule(macd, MacdRule.MacdCondition.CROSS_DOWN)
            .or(new OverIndicatorRule(rsiIndicator, 70.0));
        return sellSignal.isSatisfied(i);
    }*/

    // Агрегация Таймфрейма
    private List<Candle> aggregate(List<Candle> candles, String timeframe) {
        if (timeframe.equals("1m")) return candles; // Если 1м, ничего не делаем

        int intervalMinutes = parseTimeframe(timeframe); // Например, "15m" -> 15
        long intervalMs = intervalMinutes * 60 * 1000L;

        List<Candle> result = new ArrayList<>();

        // Группируем по интервалам времени
        Map<Long, List<Candle>> groups = candles.stream()
            .collect(Collectors.groupingBy(c -> (c.getOpenTime() / intervalMs) * intervalMs, TreeMap::new, Collectors.toList()));

        for (Map.Entry<Long, List<Candle>> entry : groups.entrySet()) {
            List<Candle> group = entry.getValue();
            Candle first = group.get(0);
            Candle last = group.get(group.size() - 1);

            Candle combined = new Candle();
            combined.setOpenTime(entry.getKey());
            combined.setOpen(first.getOpen());
            combined.setClose(last.getClose());
            combined.setHigh(group.stream().map(Candle::getHigh).max(BigDecimal::compareTo).get());
            combined.setLow(group.stream().map(Candle::getLow).min(BigDecimal::compareTo).get());
            combined.setVolume(group.stream().map(Candle::getVolume).reduce(BigDecimal.ZERO, BigDecimal::add));

            result.add(combined);
        }
        return result;
    }

    //
    private int parseTimeframe(String timeframe) {
        try {
            // Убираем все буквы, оставляем цифры
            int value = Integer.parseInt(timeframe.replaceAll("[^0-9]", ""));

            if (timeframe.toLowerCase().endsWith("h")) {
                return value * 60; // часы в минуты
            } else if (timeframe.toLowerCase().endsWith("d")) {
                return value * 60 * 24; // дни в минуты
            }
            return value; // по умолчанию считаем, что это минуты (m)
        } catch (Exception e) {
            return 1; // если произошла ошибка, возвращаем 1 минуту как дефолт
        }
    }


    // Вспомогательный метод для расчета средней
    private BigDecimal calculateSMA(List<Candle> candles, int currentIndex, int period) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int j = currentIndex; j > currentIndex - period; j--) {
            sum = sum.add(candles.get(j).getClose());
        }
        return sum.divide(new BigDecimal(period), RoundingMode.HALF_UP);
    }

    // Определяет уровни поддержки и сопротивления на основе экстремумов цен.
    // В данной реализации находит абсолютный максимум (Resistance) и минимум (Support)
    // среди переданного набора свечей.
    private List<PriceLevelDto> findSupportResistanceLevels(List<Candle> candles) {
        List<PriceLevelDto> levels = new ArrayList<>();

        // Берем последние 100 свечей и ищем самый высокий High и самый низкий Low
        BigDecimal maxHigh = candles.stream()
            .map(Candle::getHigh)
            .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        BigDecimal minLow = candles.stream()
            .map(Candle::getLow)
            .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        levels.add(new PriceLevelDto(maxHigh, "RESISTANCE"));
        levels.add(new PriceLevelDto(minLow, "SUPPORT"));

        return levels;
    }
}

/*
Как использовать:

// Цена пробила среднюю Боллинджера вверх
Rule rule1 = new CrossedUpRule(bollinger);

// RSI выше 30
Rule rule2 = new OverIndicatorRule(rsi, 30.0);

// Итоговая стратегия
Rule entryRule = rule1.and(rule2);

*/

