package com.techmatrix18.trading;

import com.techmatrix18.dto.SignalDto;
import com.techmatrix18.model.Candle;
import com.techmatrix18.service.WebSocketService;
import com.techmatrix18.telegram.TelegramService;
import com.techmatrix18.trading.indicators.*;
import com.techmatrix18.trading.rules.PriceNearFibRule;
import com.techmatrix18.trading.rules.Rule;
import com.techmatrix18.trading.rules.UnderIndicatorRule;
import com.techmatrix18.trading.series.CandleSeries;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * SignalService is responsible for analyzing price movements and generating trading signals.
 * SignalService — это «глаза», которые видят технические факты
 * Используя правила и индикаторы - генерирую сигналы для входа и выхода из сделок, которые отправляются в Telegram.
 *
 * @author Alexander Kuziv
 * @since 08.04.2026
 * @company TechMatrix18
 * @version 0.0.1
 */

@Service
public class SignalService {
    // Создаю индикаторы через new, а не жду их от Spring
    private final RsiIndicator rsiIndicator = new RsiIndicator();
    private final FibonacciIndicator fibonacciIndicator = new FibonacciIndicator(100); // задайте нужный период
    private final BollingerIndicator bollingerIndicator = new BollingerIndicator(20, 2.0);
    // Добавляю остальные, если они там есть...
    private final StrategyService strategyService;
    private final TelegramService telegramService;
    private final WebSocketService webSocketService;

    public SignalService(StrategyService strategyService, TelegramService telegramService, WebSocketService webSocketService) {
        this.strategyService = strategyService;
        this.telegramService = telegramService;
        this.webSocketService = webSocketService;
    }

    // Пример объединяет анализ Фибоначчи для генерации сигналов в Telegram
    public void checkFibonacciSignals(String symbol, CandleSeries series) {
        // 1. Индекс последней свечи
        int lastIndex = series.size() - 1;
        if (lastIndex < 1) return; // Нужно минимум 2 свечи для анализа

        // 2. Рассчитываем уровни для текущего индекса через новый индикатор
        FibLevels levels = fibonacciIndicator.calculate(series, lastIndex);

        // Проверяем, что уровни успешно рассчитались и это не EMPTY-объект
        if (levels == null || levels == FibLevels.EMPTY) return;

        // 3. Используем методы интерфейса CandleSeries
        double currentPrice = series.getClose(lastIndex);
        double prevPrice = series.getClose(lastIndex - 1);

        // Получаем уровень 0.618 через геттер (замените на ваш точный метод из FibLevels)
        double goldLevel = levels.lvl618();

        // 4. Проверяем пробой "золотого сечения"
        if (isLevelBrokenDown(currentPrice, prevPrice, goldLevel)) {
            System.out.println("СИГНАЛ: Цена " + symbol + " пробила уровень 0.618 вниз!");
        }
    }


    // Метод проверяет, пересекла ли цена уровень сверху вниз (медвежий сигнал)
    public boolean isLevelBrokenDown(double currentPrice, double previousPrice, double levelPrice) {
        return previousPrice >= levelPrice && currentPrice < levelPrice;
    }

    // Метод проверяет, пересекла ли цена уровень снизу вверх (бычий сигнал)
    public boolean isLevelBrokenUp(double currentPrice, double previousPrice, double levelPrice) {
        return previousPrice <= levelPrice && currentPrice > levelPrice;
    }

    // Пример объединяет анализ Фибоначчи и Боллинджера для генерации сигналов в Telegram
    public void analyzeMarket(String symbol, CandleSeries series) {
        // 1. Проверка на минимальное количество данных
        if (series.size() < 30) return;

        // 2. Определяем индексы (size - 1 это всегда самая свежая свеча)
        int currentIndex = series.size() - 1;
        int prevIndex = currentIndex - 1;

        // 3. ПОДГОТОВКА (Заполняем кэш индикаторов перед анализом)
        fibonacciIndicator.prepare(series);
        bollingerIndicator.prepare(series);

        // 4. Получаем актуальные цены
        double currentPrice = series.getClose(currentIndex);
        double prevPrice = series.getClose(prevIndex);

        // --- Анализ Фибоначчи ---
        // Берем уровни через getValue, так как мы вызвали prepare выше
        FibLevels fibLevels = fibonacciIndicator.getValue(currentIndex);

        // Защита от пустых данных и проверка пересечения уровня 0.618 вверх
        if (fibLevels != null && fibLevels != FibLevels.EMPTY) {
            double goldLevel = fibLevels.lvl618(); // для record используйте lvl618() без get, если у вас так в коде

            if (prevPrice <= goldLevel && currentPrice > goldLevel) {
                telegramService.sendMessageForAll("🚀 " + symbol + " пробил вверх Фибо 0.618!");
            }
        }

        // --- Анализ Боллинджера ---
        // Получаем комплексные объекты значений Боллинджера для обеих свечей
        BollingerValue currentBB = bollingerIndicator.getValue(currentIndex);
        BollingerValue prevBB = bollingerIndicator.getValue(prevIndex);

        // Проверяем, что данные Боллинджера посчитаны и не пусты
        if (currentBB != null && currentBB != BollingerValue.EMPTY &&
                prevBB != null && prevBB != BollingerValue.EMPTY) {

            // Вытаскиваем среднюю линию (basis / sma) из объектов
            // ВНИМАНИЕ: замените .getBasis() на точное имя поля/метода в вашем BollingerValue (например, .basis() или .sma)
            double basisLine = currentBB.middle();
            double prevBasisLine = prevBB.middle();

            // Проверяем пересечение средней линии
            if (prevPrice <= prevBasisLine && currentPrice > basisLine) {
                telegramService.sendMessageForAll("📈 " + symbol + " пробил среднюю линию Боллинджера вверх");
            } else if (prevPrice >= prevBasisLine && currentPrice < basisLine) {
                telegramService.sendMessageForAll("📉 " + symbol + " пробил среднюю линию Боллинджера вниз");
            }
        }
    }


    // Этот метод демонстрирует (с тестовыми данными), как можно объединить разные правила для генерации комплексных сигналов
    // Отправка сигналов (текстовых сообщений) в канал Телеграм (без скриншота графика)
    public void processSignals(String symbolName, CandleSeries series) {
        int lastIndex = series.size() - 1;
        // Для CrossedUpRule нужны минимум 2 свечи (текущая и предыдущая)
        if (lastIndex < 1) return;

        // 2. Наполняем историю индикаторов данными из текущей серии
        rsiIndicator.prepare(series);
        bollingerIndicator.prepare(series);
        fibonacciIndicator.prepare(series);

        // 3. Создаем правила
        Rule rsiOversold = new UnderIndicatorRule(rsiIndicator, 30.0);

        // ВНИМАНИЕ: Если вы еще не переписывали класс PriceNearFibRule под новый FibonacciIndicator,
        // вместо строки "level_618" логика внутри правила должна вызывать .lvl618().
        // Если класс PriceNearFibRule уже обновлен и принимает индикатор напрямую — оставляем так:
        // Передаем ссылку на метод FibLevels::lvl618 третьим аргументом
        Rule nearSupport = new PriceNearFibRule(series, fibonacciIndicator, FibLevels::lvl618, 0.001);

        // 4. Проверка условий
        if (rsiOversold.isSatisfied(lastIndex)) {
            // Получаем саму свечу (объект Candle) и Берем цену закрытия (BigDecimal)
            Candle lastCandle = series.getCandle(lastIndex);
            BigDecimal currentPrice = lastCandle.getClose();

            // Задаем проценты (1% для SL, 2% для TP)
            BigDecimal slPercent = new BigDecimal("0.01");
            BigDecimal tpPercent = new BigDecimal("0.02");
            String direction = "LONG";
            BigDecimal sl;
            BigDecimal tp;

            if ("LONG".equals(direction)) {
                // Для LONG: TP выше текущей цены, SL — ниже
                tp = currentPrice.add(currentPrice.multiply(tpPercent));
                sl = currentPrice.subtract(currentPrice.multiply(slPercent));
            } else {
                // Для SHORT: TP ниже текущей цены, SL — чаще выше
                tp = currentPrice.subtract(currentPrice.multiply(tpPercent));
                sl = currentPrice.add(currentPrice.multiply(slPercent));
            }

            // Округляем до 2 знаков после запятой (для криптовалюты)
            tp = tp.setScale(2, RoundingMode.HALF_UP);
            sl = sl.setScale(2, RoundingMode.HALF_UP);

            // 1. Расчет риска на сделку в % от цены входа
            BigDecimal priceDiffSL = currentPrice.subtract(sl).abs();
            BigDecimal riskPercent = priceDiffSL
                    .divide(currentPrice, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);

            // 2. Расчет соотношения Risk:Reward (RR)
            BigDecimal priceDiffTP = currentPrice.subtract(tp).abs();
            BigDecimal rrRatio = priceDiffTP.divide(priceDiffSL, 1, RoundingMode.HALF_UP);

            String text = String.format("📊 Сделка %s / %s\n" +
                            "Сторона: %s \n\r" +
                            "Объем: %s лот \n\r" +
                            "Цена: %s USDT\n\r" +
                            "TP: %s (2%) \n\r" +
                            "SL: %s (1%) \n\r" +
                            "Риск: %s%%\n\r" +
                            "RiskReward: 1:%s\n\r" +
                            "Сигнал: %s.",
                    symbolName,
                    "M30",
                    "LONG",
                    "1",
                    currentPrice,
                    tp,
                    sl,
                    riskPercent,
                    rrRatio,
                    "Цена подошла к уровню Фибо 0.618");

            telegramService.sendMessageForAll(text);

            // Send in WebSocket
            webSocketService.broadcastSignal(new SignalDto(System.currentTimeMillis(), symbolName, "SIGNAL", currentPrice, text));
            System.out.println("----- web socket RSI: send to websocket signal: " + text);
        }

        // Раскомментированный и исправленный блок Фибоначчи
        if (nearSupport.isSatisfied(lastIndex)) {
            // ИСПРАВЛЕНО: заменено 'symbol' на 'symbolName'
            String text = "🎯 " + symbolName + ": Цена подошла к уровню Фибо 0.618.";
            telegramService.sendMessageForAll(text);

            Candle lastCandle = series.getCandle(lastIndex);
            BigDecimal currentPrice = lastCandle.getClose();

            // Send in WebSocket
            webSocketService.broadcastSignal(new SignalDto(System.currentTimeMillis(), symbolName, "SIGNAL", currentPrice, text));
            System.out.println("----- web socket FIBO: send to websocket signal: " + text);
        }
    }

    private boolean isCrossedUp(double curr, double prev, double level) {
        return prev <= level && curr > level;
    }

    private boolean isCrossedDown(double curr, double prev, double level) {
        return prev >= level && curr < level;
    }
}

