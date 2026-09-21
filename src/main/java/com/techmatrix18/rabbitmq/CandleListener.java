package com.techmatrix18.rabbitmq;

import com.techmatrix18.config.RabbitConfig;
import com.techmatrix18.model.Candle;
import com.techmatrix18.service.PriceStorage;
import com.techmatrix18.trading.SignalService;
import com.techmatrix18.trading.StrategyService;
import com.techmatrix18.trading.indicators.RsiIndicator;
import com.techmatrix18.trading.series.LiveCandleSeries;
import com.techmatrix18.websocket.MarketDataWebSocketServer;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Candle Listener - слушатель RabbitMQ для получения свечей и анализа сигналов.
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @since 16.04.2026
 * @version 0.0.1
 */

@Service
public class CandleListener {

    private final SignalService signalService;
    private final StrategyService strategyService;
    private final PriceStorage priceStorage;
    private final MarketDataWebSocketServer webSocketServer;

    // Используем LiveCandleSeries в качестве значения
    private final Map<String, LiveCandleSeries> seriesMap = new ConcurrentHashMap<>();
    private final Map<String, RsiIndicator> rsiMap = new ConcurrentHashMap<>();

    public CandleListener(SignalService signalService, PriceStorage priceStorage, MarketDataWebSocketServer webSocketServer, StrategyService strategyService) {
        this.signalService = signalService;
        this.priceStorage = priceStorage;
        this.webSocketServer = webSocketServer;
        this.strategyService = strategyService;
    }

    /**
     * Получаю цены разных активов из WebSocket stream через RabbitMQ и обрабатываю их.
     * Цена приходит в виде Candle (раз в минуту или исторические данные), которая содержит:
     *  - поле HISTORY - если это исторические данные (отправленные при старте программы для прогрева индикаторов)
     *  - без поля HISTORY - если это живые данные (раз в минуту)
     *
     * Для ОНЛАЙН:
     * Прогреваю и заполняю LiveCandleSeries для каждого символа и таймфрейма, чтобы индикаторы могли работать.
     */
    @RabbitListener(queues = RabbitConfig.QUEUE_PRICES)
    public void onMessage(Candle candle) {
        // Получаем или создаем серию для конкретного символа
        String symbolId = candle.getSymbolId().toString();
        String symbolName = candle.getSymbol();
        String timeframe = candle.getTimeframe();
        String type = candle.getType();

        // 1. Теперь создаем LiveCandleSeries и задаем maxSize (например, 200)
        String key = symbolName + "_" + timeframe;
        LiveCandleSeries series = seriesMap.computeIfAbsent(key, k -> new LiveCandleSeries(200));

        // debug
        if ("HISTORY".equals(type)) {
            System.out.println(">>> RECEIVED HISTORY: " + key + " | Current size: " + series.size());
        } else {
            System.out.println(">>> RECEIVED : " + key + " | Current size: " + series.size());
        }

        // 1. Наполнение серии (только финализированные данные)
        if (candle.isClosed() || "HISTORY".equals(type)) {
            series.addCandle(candle);
        }

        // 2. Достаем или создаем индикатор БЕЗ прогрева внутри computeIfAbsent
        RsiIndicator rsiIndicator = rsiMap.computeIfAbsent(key, k -> new RsiIndicator());

        // 3. Работа с живым потоком (не история)
        if (!"HISTORY".equals(type)) {

            // КРИТИЧЕСКИ ВАЖНО: Если индикатор еще не видел историю, прогреваем его ОДИН РАЗ прямо перед расчетами живого потока
            // (Вызов prepare здесь безопасен, так как вся история уже гарантированно лежит в 'series')
            if (series.size() >= 14) {
                rsiIndicator.prepare(series);
            }

            double currentRsi = 50.0;

            // Если свеча закрылась — запускаем тяжелую аналитику
            if (series.size() > 14) {
                if (candle.isClosed()) {
                    // Фиксируем значение в истории индикатора
                    currentRsi = rsiIndicator.calculateIncremental(series);

                    System.out.println("Analyzing signals [" + timeframe + "] for: " + symbolName + " | Size: " + series.size());
                    signalService.processSignals(symbolName, series);
                } else {
                    // Просто считаем для плавной стрелки
                    currentRsi = rsiIndicator.calculateTemporary(series, candle.getClose().doubleValue());
                }
            }

            // Добавляем значение в объект перед отправкой
            candle.getIndicators().put("rsi", currentRsi / 100.0);

            // Шлем в React для обновления графиков и спидометров
            webSocketServer.broadcast(candle);

            // Обновляем текущую цену в хранилище
            priceStorage.updatePrice(symbolName, candle.getClose());
        }

        // Внутри CandleListener -> onMessage(Candle candle)
        // 4. МУЛЬТИ-ТАЙМФРЕЙМ ЛОГИКА (1h + 1d)
        if (!"HISTORY".equals(type) && "1h".equals(timeframe) && candle.isClosed()) {
            String key1d = symbolName + "_1d";
            LiveCandleSeries series1d = seriesMap.get(key1d); // Достаем дневную серию из кэша

            if (series1d != null && series.size() >= 30 && series1d.size() >= 30) {
                System.out.println("⚡ Запуск мульти-таймфрейм логики (1h + 1d) для " + symbolName);
                strategyService.runMultiTimeframeLogic(symbolName, series, series1d);
            } else {
                System.out.println("⚠️ Не удалось запустить MTF: дневная серия (1d) для " + symbolName + " еще не прогрета или мало свечей.");
            }
        }
    }

    // Получаю тики с ценами (каждые 100-200 мс)
    @RabbitListener(queues = RabbitConfig.QUEUE_TICKS)
    public void onTickMessage(Map<String, Object> tickData) {
        // Извлекаем данные, которые мы положили в Map в CandlePublisher
        String symbol = (String) tickData.get("symbol");
        String symbolId = tickData.get("id").toString();
        Double bid = (Double) tickData.get("bid");
        Double ask = (Double) tickData.get("ask");

        // 1. Считаем среднюю цену
        double midPrice = (bid + ask) / 2.0;
        BigDecimal midPriceBd = BigDecimal.valueOf(midPrice);
        priceStorage.updatePrice(symbol, midPriceBd);

        // Отправляю быстрые данные на frontend через WebSocket
        // создал на frontend отдельный обработчик для "BID_ASK" сообщений
        webSocketServer.broadcast(tickData);

        // Если нужно считать спред для сигналов
        // double spread = ask - bid;
        // signalService.checkScalpingSignals(symbol, bid, ask);

        // Достаем РЕАЛЬНЫЕ серии из памяти, которые ведет CandleListener
        String key1h = symbol + "_1h";  // Наш рабочий таймфрейм
        String key1d = symbol + "_1d";  // Наш старший тренд

        LiveCandleSeries series1h = seriesMap.get(key1h);
        LiveCandleSeries series1d = seriesMap.get(key1d);

        // Запускаем анализ только если обе серии уже существуют и прогреты
        if (series1h != null && series1d != null) {
            strategyService.checkTickSignal(symbol, midPrice, series1h, series1d);
        }
    }
}

