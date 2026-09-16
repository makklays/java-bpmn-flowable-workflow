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
import com.techmatrix18.trading.series.TimeframeAggregator;
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
    private final BollingerIndicator bollingerIndicator = new BollingerIndicator(20, 2.0);

    private final FibonacciIndicator fibonacciIndicator = new FibonacciIndicator(100);
    private final VolumeProfileIndicator volumeProfileIndicator = new VolumeProfileIndicator(200, 50);

    private final TelegramService telegramService;
    private final TimeframeAggregator timeframeAggregator;

    public StrategyService(TelegramService telegramService, TimeframeAggregator timeframeAggregator) {
        this.telegramService = telegramService;
        this.timeframeAggregator = timeframeAggregator;
    }

    // Соберем полноценную торговую систему.
    // Допустим, мы хотим покупать при сильном откате к Фибоначчи и продавать при перекупленности.
    public void runLogic(String symbol, CandleSeries series) {
        // Ждем накопления данных (например, для корректного расчета скользящих средних)
        if (series.size() < 200) return;

        // 1. ПОДГОТОВКА: Обновляем кэш индикаторов для всей текущей серии
        rsiIndicator.prepare(series);
        bollingerIndicator.prepare(series);
        fibonacciIndicator.prepare(series);
        // macdIndicator.prepare(series); // добавьте остальные, если они используются

        // 2. ИНДИКАТОР ЦЕНЫ: Краткая и быстрая обертка через лямбду вместо громоздкого анонимного класса
        Indicator<Double> closePrice = index -> series.getClose(index);

        // Выделяем конкретные линии Боллинджера для использования в правилах
        Indicator<Double> upperLine = index -> bollingerIndicator.getValue(index).upper();
        Indicator<Double> middleLine = index -> bollingerIndicator.getValue(index).middle();
        Indicator<Double> lowerLine = index -> bollingerIndicator.getValue(index).lower();

        // 3. ТЕКУЩИЙ ИНДЕКС: Работаем строго с последней закрытой свечой (онлайн-режим)
        int i = series.size() - 1;

        // --- ССЫЛКА НА ТЕКУЩУЮ СДЕЛКУ ---
        // Для полноценного онлайн-робота этот объект (или мапа по символам) должен храниться
        // на уровне полей класса (сервиса), чтобы робот знал, открыта ли сейчас сделка.
        // Ниже приведена логика проверки правил:

        // 4. ПРАВИЛА ВХОДА (BUY)
        Rule rsiOversold = new UnderIndicatorRule(rsiIndicator, 30.0); // RSI < 30

        // ИСПРАВЛЕНО: Раньше в CrossedUpRule передавался весь bollingerIndicator (что вызывало ошибку).
        // Теперь передаем конкретную линию — например, цена пересекает нижнюю линию Боллинджера вверх, возвращаясь в канал
        Rule bbCrossUp = new CrossedUpRule(closePrice, lowerLine);

        Rule entrySignal = rsiOversold.and(bbCrossUp);

        // Проверяем сигнал на вход
        if (entrySignal.isSatisfied(i)) {
            String msg = String.format("✅ [%s] ВХОД ЛОНГ: RSI в зоне перепроданности + Цена отскочила от нижней ленты Боллинджера!", symbol);
            telegramService.sendMessageForAll(msg);
            System.out.println(msg + " на свече №" + i);
            // exchangeService.placeMarketBuyOrder(symbol); // Пример отправки ордера
        }

        // 5. ПРАВИЛА ВЫХОДА (SELL)
        Rule rsiExit = new CrossedDownRule(rsiIndicator, 70.0); // RSI пересекает 70 сверху вниз (выход из перекупленности)

        // ИСПРАВЛЕНО: Вместо опасной строки "level_236" используем строгую типизацию через ссылку на метод рекорда FibLevels
        Rule fibTarget = new PriceNearFibRule(series, fibonacciIndicator, FibLevels::lvl236, 0.001); // Близость к уровню 23.6%

        Rule exitSignal = rsiExit.or(fibTarget);

        // Проверяем сигнал на выход
        if (exitSignal.isSatisfied(i)) {
            String msg = String.format("❌ [%s] ВЫХОД ИЗ ПОЗИЦИИ: Достигнут целевой уровень Фибоначчи 23.6%% или RSI развернулся вниз!", symbol);
            telegramService.sendMessageForAll(msg);
            System.out.println(msg + " на свече №" + i);
            // exchangeService.placeMarketSellOrder(symbol); // Пример закрытия позиции
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
        // 3. ТЕКУЩИЙ ИНДЕКС: Проверяем последнюю закрытую свечу (онлайн-режим)
        int i = series.size() - 1;
        if (i < 1) return; // Нужно минимум 2 свечи для корректной работы CrossedUpRule

        // 1. ПОДГОТОВКА: Обновляем кэш индикаторов для текущей серии
        rsiIndicator.prepare(series);
        bollingerIndicator.prepare(series);

        // 2. ИНДИКАТОР ЦЕНЫ: Короткая, быстрая лямбда благодаря дефолтному методу в интерфейсе Indicator
        Indicator<Double> closePrice = index -> series.getClose(index);

        // Извлекаем нижнюю линию Боллинджера как отдельный Indicator<Double> "на лету"
        Indicator<Double> lowerBollingerLine = index -> bollingerIndicator.getValue(index).lower();

        // 4. ПРАВИЛА
        // ИСПРАВЛЕНО: Передаем (closePrice, lowerBollingerLine).
        // Читается: "Цена закрытия пересекает НИЖНЮЮ линию Боллинджера снизу вверх" (отскок от поддержки канала)
        Rule crossedBollinger = new CrossedUpRule(closePrice, lowerBollingerLine);

        // RSI в зоне перепроданности (ниже 30)
        Rule rsiLow = new UnderIndicatorRule(rsiIndicator, 30.0);

        // ОБЪЕДИНЯЕМ: Сигнал сработает, только если оба условия выполняются одновременно
        Rule entryStrategy = crossedBollinger.and(rsiLow);

        // 5. ПРОВЕРКА СИГНАЛА
        if (entryStrategy.isSatisfied(i)) {
            telegramService.sendMessageForAll("🎯 СИГНАЛ НА ВХОД: Цена отскочила от нижней ленты Боллинджера + RSI подтверждает перепроданность!");
            System.out.println("Онлайн-сигнал зафиксирован на свече №" + i);
        }
    }

    // Добавим еще один пример для MACD + Фибоначчи
    public void checkEntryMACD(CandleSeries series) {
        // 2. ИНДЕКС: Работаем с последней закрытой свечой (онлайн-режим)
        int i = series.size() - 1;
        if (i < 1) return; // Минимум данных для анализа (нужно хотя бы 2 свечи)

        // 1. ПОДГОТОВКА: Заполняем кэш индикаторов для текущей серии
        fibonacciIndicator.prepare(series);
        macd.prepare(series);

        // 3. ПРАВИЛА
        // ИСПРАВЛЕНО: Вместо опасной строки "level_618" передаем безопасную ссылку FibLevels::lvl618.
        // 0.001 — чувствительность (0.1% отклонения от уровня золотого сечения)
        Rule nearFib = new PriceNearFibRule(series, fibonacciIndicator, FibLevels::lvl618, 0.001);

        // MacdRule адаптирован под новые рекорды MacdValue и работает мгновенно за O(1)
        Rule macdPositive = new MacdRule(macd, MacdRule.MacdCondition.ABOVE_ZERO);

        // ОБЪЕДИНЯЕМ: Условия должны совпасть одновременно на текущей свече
        Rule fibMacdStrategy = nearFib.and(macdPositive);

        // 4. ПРОВЕРКА СИГНАЛА
        if (fibMacdStrategy.isSatisfied(i)) {
            telegramService.sendMessageForAll("🎯 СИГНАЛ: Цена находится у Золотого Сечения Фибо (0.618) + Гистограмма MACD выше нуля!");
            System.out.println("Онлайн-сигнал MACD+Fib зафиксирован на свече №" + i);
        }
    }

    // Профессиональная стратегия: Тренд (EMA200) + Поддержка (Fib) + Сигнал (MACD)
    public void checkProfessionalStrategy(CandleSeries series) {
        // 2. ИНДЕКС: Последняя закрытая свеча (онлайн-режим)
        int i = series.size() - 1;
        if (i < 200) return; // Нам нужно минимум 200 свечей для корректного расчета EMA 200

        // 1. ПОДГОТОВКА: Заполняем кэш всех используемых индикаторов
        macd.prepare(series);
        fibonacciIndicator.prepare(series);

        // Инициализируем тяжелую EMA для фильтра глобального тренда
        EmaIndicator ema200 = new EmaIndicator(200);
        ema200.prepare(series);

        // Быстрая обертка цены для правила тренда
        Indicator<Double> closePrice = index -> series.getClose(index);

        // 3. ПРАВИЛА
        // Условие 1: Глобальный тренд восходящий (Цена НАД EMA 200)
        Rule trendIsUp = new OverIndicatorRule(closePrice, ema200.calculate(series, i));

        // Условие 2: Цена находится у поддержки (Передаем безопасный метод FibLevels::lvl618)
        Rule fibSupport = new PriceNearFibRule(series, fibonacciIndicator, FibLevels::lvl618, 0.001);

        // Условие 3: Точка входа (Гистограмма MACD пересекает нулевую отметку снизу вверх)
        Rule entryPoint = new MacdRule(macd, MacdRule.MacdCondition.CROSS_UP);

        // 4. ОБЪЕДИНЯЕМ И ПРОВЕРЯЕМ
        // Сигнал сработает, только если ВСЕ три условия совпали на текущей свече
        Rule fullStrategy = trendIsUp.and(fibSupport).and(entryPoint);

        if (fullStrategy.isSatisfied(i)) {
            telegramService.sendMessageForAll("💎 СИГНАЛ ВЫСОКОЙ ТОЧНОСТИ: Глобальный тренд растет, цена оттолкнулась от Фибо 0.618 + MACD подтвердил разворот импульса!");
            System.out.println("Высокоточный профессиональный сигнал зафиксирован на свече №" + i);
        }
    }

    // Сигнал на выход - проверим, не выдыхается ли тренд.
// Если RSI в зоне перекупленности И импульс MACD затухает — пора фиксировать прибыль.
    public void checkExitStrategy(CandleSeries series) {
        // 2. ИНДЕКС: Последняя закрытая свеча (онлайн-режим)
        int i = series.size() - 1;
        // Нам нужно минимум 2 свечи (индексы i и i-1) для проверки падения гистограммы в MacdRule
        if (i < 1) return;

        // 1. ПОДГОТОВКА: Заполняем кэш для корректной работы getValue(i)
        rsiIndicator.prepare(series);
        macd.prepare(series);

        // 3. ПРАВИЛА
        // Условие 1: Мы находимся в зоне перекупленности по RSI (выше 70)
        Rule overbought = new OverIndicatorRule(rsiIndicator, 70.0);

        // Условие 2: ИСПРАВЛЕНО: Импульс MACD начал затухать (столбик гистограммы стал меньше предыдущего)
        Rule momentumFading = new MacdRule(macd, MacdRule.MacdCondition.HIST_FALLING);

        // 4. ОБЪЕДИНЯЕМ И ПРОВЕРЯЕМ
        Rule exitStrategy = overbought.and(momentumFading);

        if (exitStrategy.isSatisfied(i)) {
            telegramService.sendMessageForAll("⚠️ [%s] СИГНАЛ НА ВЫХОД: Тренд выдыхается! RSI > 70 (перекупленность) + Гистограмма MACD падает. Рекомендуется фиксация прибыли.");
            System.out.println("Онлайн-сигнал на выход (фиксацию) зафиксирован на свече №" + i);
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
    public void executeFullAnalysis(String symbol, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return;

        // 1. Оборачиваем данные в серию
        CandleSeries series = new HistoricalCandleSeries(candles);

        // Защита: для стабильной работы индикаторов (например, тяжелых EMA внутри MACD) нужно накопить историю
        if (series.size() < 100) return;

        // 2. ПОДГОТОВКА: Заполняем кэш всех используемых индикаторов для всей серии
        macd.prepare(series);
        rsiIndicator.prepare(series);
        fibonacciIndicator.prepare(series);

        // 3. ТЕКУЩИЙ ИНДЕКС: Последняя закрытая свеча (анализ ситуации в текущий момент времени)
        int i = series.size() - 1;

        // 4. СТРАТЕГИЯ ВХОДА (BUY)
        // ИСПРАВЛЕНО: Вместо строки "level_618" передаем безопасную ссылку FibLevels::lvl618
        Rule buySignal = new MacdRule(macd, MacdRule.MacdCondition.CROSS_UP)
                .and(new UnderIndicatorRule(rsiIndicator, 40.0))
                .and(new PriceNearFibRule(series, fibonacciIndicator, FibLevels::lvl618, 0.001));

        if (buySignal.isSatisfied(i)) {
            telegramService.sendMessageForAll(String.format("🚀 [%s] СИГНАЛ НА ВХОД: MACD Cross + Отскок от Фибо 0.618 + RSI < 40", symbol));
        }

        // 5. СТРАТЕГИЯ ВЫХОДА (SELL / EXIT)
        // ИСПРАВЛЕНО: Вместо строки "level_236" передаем безопасную ссылку FibLevels::lvl236
        Rule sellSignal = new MacdRule(macd, MacdRule.MacdCondition.CROSS_DOWN)
                .or(new OverIndicatorRule(rsiIndicator, 70.0))
                .or(new PriceNearFibRule(series, fibonacciIndicator, FibLevels::lvl236, 0.001));

        if (sellSignal.isSatisfied(i)) {
            telegramService.sendMessageForAll(String.format("⚠️ [%s] СИГНАЛ НА ВЫХОД: Тренд развернулся по MACD, RSI перекуплен (>70) или достигнута цель Фибо 23.6%%", symbol));
        }
    }

    // Выполняет бэкtest стратегии на истории: рассчитывает сигналы входа/выхода
    public BacktestDto analyzeHistory(List<Candle> candles, String timeframe) {
        // 1. Агрегируем свечи (1m -> выбранный таймфрейм)
        List<Candle> aggregatedList = timeframeAggregator.aggregate(candles, timeframe);

        // 2. Оборачиваем в нашу универсальную серию
        CandleSeries series = new HistoricalCandleSeries(aggregatedList);

        // 3. ПОДГОТОВКА ИНДИКАТОРОВ (Считаем всю историю один раз)
        ema50.prepare(series);
        ema200.prepare(series);
        macd.prepare(series);
        rsiIndicator.prepare(series);
        stochasticIndicator.prepare(series);

        BacktestDto report = new BacktestDto();
        // Отправляем на фронтенд именно агрегированные свечи
        report.setCandles(CandleMapper.toDtoList(aggregatedList));

        List<SignalDto> signals = new ArrayList<>();
        List<TradeDto> trades = new ArrayList<>();

        BigDecimal stopLossPercent = new BigDecimal("3.0"); // Стоп-лосс 3%
        TradeDto currentTrade = null;

        // ИСПРАВЛЕНО: Начинаем с 200 свечи, чтобы тяжелые индикаторы (EMA 200) "прогрелись" данными
        for (int i = 200; i < series.size(); i++) {
            Candle currentCandle = series.getCandle(i);
            BigDecimal closePrice = currentCandle.getClose();

            // ЕСЛИ СДЕЛКА НЕ ОТКРЫТА — ИЩЕМ ВХОД
            if (currentTrade == null) {
                if (checkBuyCondition(series, i)) {
                    // Входим по цене закрытия сигнальной свечи i
                    currentTrade = new TradeDto(currentCandle.getOpenTime(), closePrice, "BUY");

                    // РАССЧИТЫВАЕМ ЦЕНУ СТОП-ЛОССА СРАЗУ ПРИ ВХОДЕ
                    BigDecimal slFactor = BigDecimal.ONE.subtract(stopLossPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
                    currentTrade.setStopLoss(closePrice.multiply(slFactor));

                    signals.add(new SignalDto(currentCandle.getOpenTime(), currentCandle.getSymbol(), "BUY", closePrice, "Entry"));
                }
            }
            // ЕСЛИ СДЕЛКА ОТКРЫТА — ИЩЕМ ВЫХОД
            else {
                // ИСПРАВЛЕНО: Сравниваем МИНИМУМ свечи (Low) со стоп-лоссом для 100% реалистичности теста
                boolean isStopLossTriggered = currentCandle.getLow().compareTo(currentTrade.getStopLoss()) <= 0;

                // Проверка по индикаторам стратегии
                boolean isIndicatorExit = checkSellCondition(series, i);

                if (isIndicatorExit || isStopLossTriggered) {
                    currentTrade.setExitTime(currentCandle.getOpenTime());

                    // ИСПРАВЛЕНО ДЛЯ ЛОГОВ: Если сработал стоп, фиксируем цену выхода именно по уровню стопа,
                    // а не по цене закрытия, иначе финансовый результат посчитается неверно
                    BigDecimal exactExitPrice = isStopLossTriggered ? currentTrade.getStopLoss() : closePrice;
                    currentTrade.setExitPrice(exactExitPrice);

                    // Расчет профита/убытка
                    BigDecimal diff = currentTrade.getExitPrice().subtract(currentTrade.getEntryPrice());
                    currentTrade.setProfit(diff);

                    // ИСПРАВЛЕНО: Используем актуальный RoundingMode.HALF_UP вместо устаревшего целого числа
                    currentTrade.setProfitPercent(diff.divide(currentTrade.getEntryPrice(), 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")));

                    trades.add(currentTrade);

                    // Пометка в сигнале, по какой причине вышли
                    String exitReason = isStopLossTriggered ? "Stop Loss" : "Indicator Exit";
                    signals.add(new SignalDto(currentCandle.getOpenTime(), currentCandle.getSymbol(), "SELL", exactExitPrice, exitReason));

                    currentTrade = null; // Позиция закрыта, обнуляем сессию
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
        // Защита: для пересечений Стохастика и индикаторов нужно минимум 2 свечи
        if (i < 1) return false;

        // 1. ИНДИКАТОР ЦЕНЫ
        Indicator<Double> closePrice = index -> series.getClose(index);

        // 2. БЫСТРОЕ ПОЛУЧЕНИЕ ИЗ КЭША (O(1) - благодаря предварительному prepare в analyzeHistory)
        double val50 = ema50.getValue(i);
        double val200 = ema200.getValue(i);
        double price = series.getClose(i);

        // Выделяем линии Стохастика (%K и %D) как независимые Indicator<Double> через лямбды
        Indicator<Double> kLine = index -> stochasticIndicator.getValue(index).k();
        Indicator<Double> dLine = index -> stochasticIndicator.getValue(index).d();

        // 3. СТРОИМ ЛОГИЧЕСКИЕ ПРАВИЛА
        // Фильтр тренда: Цена выше обеих ЕМА
        boolean isPriceAboveEma = price > val50 && price > val200;

        // Осцилляторы
        Rule rsiLow = new UnderIndicatorRule(rsiIndicator, 30.0); // RSI < 30
        Rule stochOversold = new UnderIndicatorRule(kLine, 30.0); // Линия %K в зоне перепроданности

        // Точки входа (пересечения)
        Rule macdCrossUp = new MacdRule(macd, MacdRule.MacdCondition.CROSS_UP); // MACD Cross Up
        Rule stochCrossUp = new CrossedUpRule(kLine, dLine);                   // Стохастик %K пересекает %D вверх

        // 4. КОМБИНИРУЕМ СТРАТЕГИЮ
        // Вариант 1 (Ваш закомментированный): ТРЕНД + ИМПУЛЬС MACD
        // Rule strategy = new OverIndicatorRule(closePrice, val50).and(...)

        // Вариант 2 (Ваш текущий активный return): RSI перепродан + Стохастик развернулся в зоне перепроданности
        Rule currentStrategy = rsiLow.and(stochOversold).and(stochCrossUp);

        // Если хотите добавить фильтр тренда к текущей стратегии, раскомментируйте строчку ниже:
        // return isPriceAboveEma && currentStrategy.isSatisfied(i);

        return currentStrategy.isSatisfied(i);
    }

    // Обновленный метод продажи (принимает Series и Index)
    private boolean checkSellCondition(CandleSeries series, int i) {
        // Защита: для фиксации пересечений нужно минимум 2 свечи
        if (i < 1) return false;

        // 1. БЫСТРОЕ ПОЛУЧЕНИЕ ИЗ КЭША (O(1) - данные уже посчитаны в основном цикле)
        double val50 = ema50.getValue(i);
        double val200 = ema200.getValue(i);
        double price = series.getClose(i);

        // Выделяем линии Стохастика (%K и %D) как независимые индикаторы через лямбды
        Indicator<Double> kLine = index -> stochasticIndicator.getValue(index).k();
        Indicator<Double> dLine = index -> stochasticIndicator.getValue(index).d();

        // 2. СТРОИМ ЛОГИЧЕСКИЕ ПРАВИЛА
        // Тренд (цена упала под скользящие средние)
        boolean isPriceBelowEma = price < val50 && price < val200;

        // Осцилляторы
        Rule rsiHigh = new OverIndicatorRule(rsiIndicator, 70.0);       // RSI > 70
        Rule stochOverbought = new OverIndicatorRule(kLine, 70.0);      // %K в зоне перекупленности > 70

        // Точки выхода (пересечения)
        Rule stochCrossDown = new CrossedDownRule(kLine, dLine);        // %K пересекает %D сверху вниз
        Rule macdCrossDown = new MacdRule(macd, MacdRule.MacdCondition.CROSS_DOWN);

        // 3. КОМБИНИРУЕМ СТРАТЕГИЮ ВЫХОДА
        // Выходим, когда RSI перекуплен + Стохастик пересекся сверху вниз в зоне перекупленности
        Rule currentExitStrategy = rsiHigh.and(stochOverbought).and(stochCrossDown);

        // Если вы хотите выходить также при развороте глобального тренда (цена под EMA)
        // или по сигналу MACD, вы можете объединить их через .or()
        // Rule complexExit = currentExitStrategy.or(macdCrossDown).or(index -> isPriceBelowEma);

        return currentExitStrategy.isSatisfied(i);
    }
    /*private boolean checkSellCondition(CandleSeries series, int i) {
        Rule sellSignal = new MacdRule(macd, MacdRule.MacdCondition.CROSS_DOWN)
            .or(new OverIndicatorRule(rsiIndicator, 70.0));
        return sellSignal.isSatisfied(i);
    }*/

    /**
     * Вспомогательный метод для расчета SMA на примитивах.
     * Работает быстро и безопасно в рамках текущего индекса.
     */
    private double calculateSMA(CandleSeries series, int currentIndex, int period) {
        if (currentIndex < period - 1) return 0.0;

        double sum = 0.0;
        for (int j = currentIndex; j > currentIndex - period; j--) {
            sum += series.getClose(j);
        }
        return sum / period;
    }

    /**
     * ИСПРАВЛЕНО: Находит уровни поддержки и сопротивления на основе скользящего окна (lookback).
     * Защищено от заглядывания в будущее (Look-ahead bias).
     *
     * @param series универсальная серия свечей
     * @param currentIndex текущая свеча в цикле бэктестера
     * @param lookback глубина поиска назад (например, 100 свечей)
     */
    private List<PriceLevelDto> findSupportResistanceLevels(CandleSeries series, int currentIndex, int lookback) {
        List<PriceLevelDto> levels = new ArrayList<>();
        if (series.size() == 0 || currentIndex < 0) return levels;

        // Определяем честные динамические границы скользящего окна
        int start = Math.max(0, currentIndex - lookback + 1);

        // Инициализируем экстремумы первой свечей окна
        double maxHigh = series.getHigh(start);
        double minLow = series.getLow(start);

        // Линейный поиск экстремумов строго до текущего индекса включительно
        for (int j = start + 1; j <= currentIndex; j++) {
            double currentHigh = series.getHigh(j);
            double currentLow = series.getLow(j);

            if (currentHigh > maxHigh) maxHigh = currentHigh;
            if (currentLow < minLow) minLow = currentLow;
        }

        // Конвертируем обратно в BigDecimal только на этапе создания DTO для фронтенда
        levels.add(new PriceLevelDto(BigDecimal.valueOf(maxHigh), "RESISTANCE"));
        levels.add(new PriceLevelDto(BigDecimal.valueOf(minLow), "SUPPORT"));

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

