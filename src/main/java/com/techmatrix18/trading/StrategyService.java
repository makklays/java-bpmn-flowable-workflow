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
import java.util.concurrent.ConcurrentHashMap;
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

    // Карты состояний для предотвращения спама (Ключ = Имя монеты, Значение = Был ли сигнал)
    private final Map<String, Boolean> activeLongSignals = new ConcurrentHashMap<>();
    private final Map<String, Boolean> activeShortSignals = new ConcurrentHashMap<>();

    public StrategyService(TelegramService telegramService, TimeframeAggregator timeframeAggregator) {
        this.telegramService = telegramService;
        this.timeframeAggregator = timeframeAggregator;
    }

    /**
     * Основной метод, который вызывается ИЗ КАНАЛА с меньшим таймфреймом (15m или 1h) в CandleListener
     * 21.09.2026:
     */
    public void runMultiTimeframeLogic(String symbol, CandleSeries series1H, CandleSeries series1d) {
        // Проверяем, что в обеих сериях накопилось достаточно свечей для расчетов
        if (series1H.size() < 30 || series1d.size() < 30) return;

        // Ключ для блокировки спама именно для рабочего таймфрейма этой монеты
        String spamKey = symbol + "_1h";

        // ========================================================
        // ЭКРАН 1: ОПРЕДЕЛЯЕМ СТАРШИЙ ТРЕНД НА 1h СВЕЧАХ
        // ========================================================
        bollingerIndicator.prepare(series1d); // Считаем Боллинджер по часовым свечам
        int lastIdx1h = series1d.size() - 1;

        double price1h = series1d.getClose(lastIdx1h);
        double middleLine1h = bollingerIndicator.getValue(lastIdx1h).middle();

        // Фильтры тренда:
        boolean isTrendUp1h = price1h > middleLine1h;   // Тренд вверх, если цена на 1h выше средней линии
        boolean isTrendDown1h = price1h < middleLine1h; // Тренд вниз, если цена на 1h ниже средней линии

        // ========================================================
        // ЭКРАН 2: ИЩЕМ СИГНАЛ НА РАБОЧЕМ ТАЙМФРЕЙМЕ M15
        // ========================================================
        rsiIndicator.prepare(series1H);        // Пересчитываем RSI под 1H свечи
        bollingerIndicator.prepare(series1H);  // Пересчитываем Боллинджер под 1H свечи
        fibonacciIndicator.prepare(series1H);  // Пересчитываем Фибо под 1H свечи

        Indicator<Double> closePriceM15 = index -> series1H.getClose(index);
        Indicator<Double> lowerLineM15 = index -> bollingerIndicator.getValue(index).lower();

        int i = series1H.size() - 1;

        // Правила рабочего таймфрейма
        Rule rsiOversold = new UnderIndicatorRule(rsiIndicator, 30.0);
        Rule bbCrossUp = new CrossedUpRule(closePriceM15, lowerLineM15);
        Rule entrySignalM15 = rsiOversold.and(bbCrossUp);


        // ========================================================
        // ЭКРАН 3: СВЕРКА СИГНАЛА С ТРЕНДОМ И ВХОД
        // ========================================================

        // --- ПРОГРАММА ЛОНГ ---
        if (entrySignalM15.isSatisfied(i)) {
            // КРИТИЧЕСКИЙ ФИЛЬТР: Входим в лонг на 1H только если на 1h тренд БЫЧИЙ
            if (isTrendUp1h) {
                boolean alreadySent = activeLongSignals.getOrDefault(spamKey, false);

                if (!alreadySent) {
                    BigDecimal currentPrice = BigDecimal.valueOf(series1H.getClose(i));
                    // ... (Ваш расчет математики TP/SL и рисков как в предыдущих примерах) ...

                    String text = String.format("🚀 [MTF СИГНАЛ] ВХОД ЛОНГ по %s (1h)\n" +
                                    "Фильтр тренда (1d): ТРЕНД ВОСХОДЯЩИЙ (Цена %.2f > Middle %.2f)\n" +
                                    "Цена входа: %s USDT",
                            symbol, price1h, middleLine1h, currentPrice);

                    telegramService.sendMessageForAll(text);
                    activeLongSignals.put(spamKey, true);
                }
            } else {
                System.out.println("⚠️ СИГНАЛ ЛОНГ по " + symbol + " на 1h проигнорирован: старший тренд 1d против нас (Медвежий).");
            }
        } else {
            activeLongSignals.put(spamKey, false);
        }

        // --- ПРОГРАММА ШОРТ ---
        Rule rsiExit = new CrossedDownRule(rsiIndicator, 70.0);
        Rule fibTarget = new PriceNearFibRule(series1H, fibonacciIndicator, FibLevels::lvl236, 0.001);
        Rule exitSignalM15 = rsiExit.or(fibTarget);

        if (exitSignalM15.isSatisfied(i)) {
            // КРИТИЧЕСКИЙ ФИЛЬТР: Входим в шорт на 1H только если на 1h тренд МЕДВЕЖИЙ
            if (isTrendDown1h) {
                boolean alreadySent = activeShortSignals.getOrDefault(spamKey, false);

                if (!alreadySent) {
                    BigDecimal currentPrice = BigDecimal.valueOf(series1H.getClose(i));
                    // ... (Ваш зеркальный расчет математики TP/SL для шорта) ...

                    String text = String.format("🔻 [MTF СИГНАЛ] ВХОД ШОРТ по %s (1h)\n" +
                                    "Фильтр тренда (1d): ТРЕНД НИСХОДЯЩИЙ (Цена %.2f < Middle %.2f)\n" +
                                    "Цена входа: %s USDT",
                            symbol, price1h, middleLine1h, currentPrice);

                    telegramService.sendMessageForAll(text);
                    activeShortSignals.put(spamKey, true);
                }
            } else {
                System.out.println("⚠️ СИГНАЛ ШОРТ по " + symbol + " на 1h проигнорирован: старший тренд 1d против нас (Бычий).");
            }
        } else {
            activeShortSignals.put(spamKey, false);
        }
    }

    // Соберем полноценную торговую систему.
    // 21.09.2026: Обновленный метод для работы с данными тиками и свечами в реальном времени.
    // Допустим, мы хотим покупать при сильном откате к Фибоначчи и продавать при перекупленности.
    public void runLogic(String symbol, CandleSeries series) {
        // Ждем накопления данных (например, для корректного расчета скользящих средних)
        if (series.size() < 200) return;

        // 1. Подготовка индикаторов
        rsiIndicator.prepare(series);
        bollingerIndicator.prepare(series);
        fibonacciIndicator.prepare(series);

        // 2. Обертки линий
        Indicator<Double> closePrice = index -> series.getClose(index);
        // Выделяем конкретные линии Боллинджера для использования в правилах
        Indicator<Double> upperLine = index -> bollingerIndicator.getValue(index).upper();
        Indicator<Double> middleLine = index -> bollingerIndicator.getValue(index).middle();
        Indicator<Double> lowerLine = index -> bollingerIndicator.getValue(index).lower();

        // ТЕКУЩИЙ ИНДЕКС: Работаем строго с последней закрытой свечой (онлайн-режим)
        int i = series.size() - 1;

        // --- ССЫЛКА НА ТЕКУЩУЮ СДЕЛКУ ---
        // Для полноценного онлайн-робота этот объект (или мапа по символам) должен храниться
        // на уровне полей класса (сервиса), чтобы робот знал, открыта ли сейчас сделка.
        // Ниже приведена логика проверки правил:

        // 3. Правила входа (BUY)
        Rule rsiOversold = new UnderIndicatorRule(rsiIndicator, 30.0);
        Rule bbCrossUp = new CrossedUpRule(closePrice, lowerLine);
        Rule entrySignal = rsiOversold.and(bbCrossUp);

        // 4. ЕСЛИ СИГНАЛ СРАБОТАЛ — управляем сделкой (перенесено из старого SignalService)
        if (entrySignal.isSatisfied(i)) {
            // Проверяем, отправляли ли мы уже этот сигнал на предыдущих свечах
            boolean alreadySent = activeLongSignals.getOrDefault(symbol, false);

            if (!alreadySent) {
                Candle lastCandle = series.getCandle(i);
                BigDecimal currentPrice = lastCandle.getClose();

                // Менеджмент капитала и рисков (Бизнес-логика Стратегии!)
                BigDecimal slPercent = new BigDecimal("0.01"); // 1%
                BigDecimal tpPercent = new BigDecimal("0.02"); // 2%

                BigDecimal tp = currentPrice.add(currentPrice.multiply(tpPercent)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal sl = currentPrice.subtract(currentPrice.multiply(slPercent)).setScale(2, RoundingMode.HALF_UP);

                BigDecimal priceDiffSL = currentPrice.subtract(sl).abs();
                BigDecimal riskPercent = priceDiffSL.divide(currentPrice, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal priceDiffTP = currentPrice.subtract(tp).abs();
                BigDecimal rrRatio = priceDiffTP.divide(priceDiffSL, 1, RoundingMode.HALF_UP);

                String text = String.format("📊 СТРАТЕГИЯ СРАБОТАЛА: ВХОД ЛОНГ по %s\n" +
                                "Цена входа: %s USDT\n" +
                                "TP (Target): %s | SL (Stop): %s\n" +
                                "Риск на сделку: %s%% | RiskReward: 1:%s",
                        symbol, currentPrice, tp, sl, riskPercent, rrRatio);

                telegramService.sendMessageForAll(text);

                // Тут будет вызов сервиса биржи для реальной покупки:
                // exchangeService.openPosition(symbol, "LONG", currentPrice, tp, sl);

                // Включаем флаг блокировки спама для ЛОНГА по этой монете
                activeLongSignals.put(symbol, true);

                // Гасим сигнал ШОРТА, так как мы развернулись в лонг
                activeShortSignals.put(symbol, false);
            }
        } else {
            // Если условия сигнала ЛОНГ больше не выполняются, сбрасываем флаг,
            // чтобы при следующем заходе в зону фильтр пропустил новое уведомление
            activeLongSignals.put(symbol, false);
        }

        // 5. ПРАВИЛА ВЫХОДА (SELL)
        Rule rsiExit = new CrossedDownRule(rsiIndicator, 70.0); // RSI пересекает 70 сверху вниз (выход из перекупленности)
        Rule fibTarget = new PriceNearFibRule(series, fibonacciIndicator, FibLevels::lvl236, 0.001); // Близость к уровню 23.6%
        Rule exitSignal = rsiExit.or(fibTarget);

        // 6. ЕСЛИ СИГНАЛ СРАБОТАЛ — управляем сделкой SHORT / ВЫХОД
        if (exitSignal.isSatisfied(i)) {
            // Проверяем, отправляли ли мы уже шорт-сигнал ранее
            boolean alreadySent = activeShortSignals.getOrDefault(symbol, false);

            if (!alreadySent) {
                Candle lastCandle = series.getCandle(i);
                BigDecimal currentPrice = lastCandle.getClose();

                BigDecimal slPercent = new BigDecimal("0.01"); // 1%
                BigDecimal tpPercent = new BigDecimal("0.02"); // 2%

                // ЗЕРКАЛЬНО ДЛЯ SHORT: Тейк ниже, Стоп выше цены входа
                BigDecimal tp = currentPrice.subtract(currentPrice.multiply(tpPercent)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal sl = currentPrice.add(currentPrice.multiply(slPercent)).setScale(2, RoundingMode.HALF_UP);

                // Расчет риска (для шорта формула разницы модулей остается точной)
                BigDecimal priceDiffSL = currentPrice.subtract(sl).abs();
                BigDecimal riskPercent = priceDiffSL.divide(currentPrice, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal priceDiffTP = currentPrice.subtract(tp).abs();
                BigDecimal rrRatio = priceDiffTP.divide(priceDiffSL, 1, RoundingMode.HALF_UP);

                String text = String.format("🔻 СТРАТЕГИЯ СРАБОТАЛА: ВХОД ШОРТ (ВЫХОД) по %s\n" +
                                "Причина: Достигнут уровень Фибо 23.6%% или RSI развернулся из перекупленности\n" +
                                "Цена входа: %s USDT\n" +
                                "TP (Target): %s | SL (Stop): %s\n" +
                                "Риск на сделку: %s%% | RiskReward: 1:%s",
                        symbol, currentPrice, tp, sl, riskPercent, rrRatio);

                telegramService.sendMessageForAll(text);
                System.out.println(text + " на свече №" + i);
                // exchangeService.openPosition(symbol, "SHORT", currentPrice, tp, sl);

                // Включаем флаг блокировки спама для ШОРТА по этой монете
                activeShortSignals.put(symbol, true);

                // Гасим сигнал ЛОНГА, так как мы ушли в шорт
                activeLongSignals.put(symbol, false);
            }
        } else {
            // Если условия сигнала ШОРТ пропали, сбрасываем флаг для будущих сигналов
            activeShortSignals.put(symbol, false);
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
     * @param series       универсальная серия свечей
     * @param currentIndex текущая свеча в цикле бэктестера
     * @param lookback     глубина поиска назад (например, 100 свечей)
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

    /**
     * Метод для проверки сигнала на основе старшего таймфрейма (HTF) и младшего таймфрейма (LTF).
     */
    public void checkTickSignal(String symbol, double midPrice, CandleSeries seriesLtf, CandleSeries seriesHtf) {
        // Защита: если истории в сериях еще недостаточно для индикаторов — выходим
        if (seriesLtf.size() < 30 || seriesHtf.size() < 30) return;

        // 1. Инициализируем и подготавливаем индикаторы для СТАРШЕГО таймфрейма (htf)
        BollingerIndicator bollingerHtf = new BollingerIndicator(20, 2.0);
        bollingerHtf.prepare(seriesHtf);

        int lastIdxHtf = seriesHtf.size() - 1;
        double priceHtf = seriesHtf.getClose(lastIdxHtf);
        double middleLineHtf = bollingerHtf.getValue(lastIdxHtf).middle();

        // Определяем старший тренд
        boolean isTrendUp = priceHtf > middleLineHtf;

        // 2. Инициализируем и подготавливаем индикаторы для РАБОЧЕГО таймфрейма (ltf)
        BollingerIndicator bollingerLtf = new BollingerIndicator(20, 2.0);
        bollingerLtf.prepare(seriesLtf);

        int lastIdxLtf = seriesLtf.size() - 1;
        double lowerBb = bollingerLtf.getValue(lastIdxLtf).lower();

        String spamKey = symbol + "_TICK";

        // 3. ПРАВИЛО ВХОДА В ЛОНГ НА ОСНОВЕ ЦЕНЫ ТИКА
        // Проверяем: старший тренд вверх И текущая цена тика пробила нижний Боллинджер рабочего таймфрейма
        if (isTrendUp && midPrice <= lowerBb) {
            boolean alreadySent = activeLongSignals.getOrDefault(spamKey, false);

            if (!alreadySent) {
                String text = String.format("🔥 [ONLINE ТИК-СИГНАЛ] ВХОД ЛОНГ по %s\n" +
                                "Цена тика (%.2f) пробила нижнюю ленту Боллинджера (%.2f)!\n" +
                                "Старший тренд подтверждает рост 📈",
                        symbol, midPrice, lowerBb);

                telegramService.sendMessageForAll(text);
                activeLongSignals.put(spamKey, true); // Защита от спама
            }
        }
    }
}

/*
Пример построения комплексной торговой стратегии на вход (BUY)

// 1. Выделяем необходимые линии индикаторов как независимые Indicator<Double>
Indicator<Double> closePrice = index -> series.getClose(index);
Indicator<Double> bbMiddleLine = index -> bollingerIndicator.getValue(index).middle();

// 2. Строим отдельные логические правила:
// Условие А: Цена закрытия пробила Среднюю линию Боллинджера (Basis) снизу вверх
Rule rule1 = new CrossedUpRule(closePrice, bbMiddleLine);

// Условие Б: Индикатор RSI находится выше уровня 30.0 (выход из зоны перепроданности)
Rule rule2 = new OverIndicatorRule(rsiIndicator, 30.0);

// 3. Объединяем правила через Fluent API (условия должны выполниться одновременно)
Rule entryRule = rule1.and(rule2);

// 4. Проверяем сигнал на последней закрытой свече 'i'
if (entryRule.isSatisfied(i)) {
    telegramService.sendMessageForAll("🎯 СИГНАЛ НА ВХОД: Боллинджер пробит вверх + RSI подтверждает!");
}

*/

