package com.techmatrix18.clients;

import com.techmatrix18.model.Candle;
import com.techmatrix18.rabbitmq.CandlePublisher;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Binance Kline Web Socket - Socket для получения данных свечей в он-лайне
 *
 * message - JSON-сообщение как строка:
 * {
 *   "e": "kline",
 *   "E": 1678280000000,
 *   "s": "BTCUSDT",
 *   "k": {
 *     "t": 1678280000000,
 *     "T": 1678280059999,
 *     "s": "BTCUSDT",
 *     "i": "1m",
 *     "f": 123456,
 *     "L": 123789,
 *     "o": "27000.00",
 *     "c": "27050.00",
 *     "h": "27100.00",
 *     "l": "26950.00",
 *     "v": "1.234",
 *     "x": true
 *   }
 * }
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @since 16.04.2026
 * @version 0.0.1
 */

public class BinanceKlineWebSocket {
    private WebSocketClient webSocketClient;
    private String symbol;
    private final Integer symbolId;
    private final String timeframe;

    private final CandlePublisher candlePublisher;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public BinanceKlineWebSocket(String symbol, Integer symbolId, String timeframe, CandlePublisher candlePublisher) {
        this.symbol = symbol;
        this.symbolId = symbolId;
        this.timeframe = timeframe;
        this.candlePublisher = candlePublisher;
    }

    public void connect(String symbol, String interval) {
        String url = String.format("wss://stream.binance.com:9443/ws/%s@kline_%s", symbol.toLowerCase(), interval);

        try {
            webSocketClient = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("WebSocket открыт для символа " + symbol.toLowerCase());
                }
                @Override
                public void onMessage(String message) {
                    // 1. Парсим JSON в объект Candle
                    Candle candle = parseMessage(message);

                    // 2. Если свеча закрыта (isFinal), отправляем её в менеджер
                    /*if (candle != null ) {  // && candle.isClosed() - если нужно только закрытые свечи
                        // Sending an event to RabbitMQ
                        candlePublisher.publishCandle(candle);

                        // log
                        String openTimeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(candle.getOpenTime()), ZoneId.systemDefault()).format(formatter);
                        String closeTimeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(candle.getCloseTime()), ZoneId.systemDefault()).format(formatter);

                        //System.out.println(String.format("Свеча: %s – %s | O:%.2f H:%.2f L:%.2f C:%.2f V:%.2f Final:%s",
                        //        openTimeStr, closeTimeStr, candle.getOpen(), candle.getHigh(),
                        //        candle.getLow(), candle.getClose(), candle.getVolume(), candle.isClosed()));
                    }*/

                    if (candle != null) {
                        candlePublisher.publishCandle(candle);
                    }
                }
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("WebSocket закрыт для символа " + symbol.toLowerCase() + ": " + reason);
                }
                @Override
                public void onError(Exception ex) {
                    System.out.println("Ошибка WebSocket для символа " + symbol.toLowerCase() + ": " + ex.getMessage());
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Преобразует входящее JSON-сообщение от Binance WebSocket в объект Candle.
     * Метод извлекает данные из объекта "k" (kline), который содержит параметры свечи.
     * Реализована проверка на наличие ключа "k", чтобы игнорировать системные сообщения
     * (ответы на пинг, подтверждения подписки и т.д.). В случае ошибки парсинга
     * или неверного формата данных метод возвращает null, предотвращая сбой потока.
     */
    private Candle parseMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);

            // Проверяем наличие ключа "k", прежде чем лезть в него
            if (!json.has("k")) return null;

            JSONObject k = json.getJSONObject("k");

            Candle candle = new Candle();
            // Заполняем метаданные из полей класса
            candle.setExchangeId(1); // ID = 1 - Binance
            candle.setSymbolId(Long.valueOf(this.symbolId));
            candle.setSymbol(this.symbol);
            candle.setType("CANDLE"); // для отправки в Вебсокет на React
            candle.setTimeframe(this.timeframe);
            // Значения symbolId и timeframe можно установить здесь, если они переданы в сокет
            candle.setOpenTime(k.getLong("t"));
            candle.setCloseTime(k.getLong("T"));
            candle.setOpen(k.getBigDecimal("o"));
            candle.setHigh(k.getBigDecimal("h"));
            candle.setLow(k.getBigDecimal("l"));
            candle.setClose(k.getBigDecimal("c"));
            candle.setVolume(k.getBigDecimal("v"));
            candle.setIsClosed(k.getBoolean("x")); // Флаг финализации свечи (is final)

            return candle;

        } catch (Exception e) {
            System.err.println("Ошибка парсинга WebSocket сообщения: " + e.getMessage());
            return null;
        }
    }

    //
    public void close() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }

    // Едино разовое получение цены
    public void getSinglePrice(java.util.function.Consumer<Double> callback) {
        // 1. Приводим символ к нижнему регистру и убираем пробелы
        String cleanSymbol = this.symbol.trim().toLowerCase();
        // 2. Убедитесь, что timeframe передается как "1m", "1h" и т.д.
        // Если timeframe — это объект вашего Enum, используйте его строковое поле
        String cleanTimeframe = this.timeframe.toLowerCase();
        String url = String.format("wss://stream.binance.com:9443/ws/%s@kline_%s", cleanSymbol, cleanTimeframe);
        try {
            System.out.println("Попытка подключения " + cleanSymbol + " к: " + url);
            webSocketClient = new WebSocketClient(new URI(url)) {
                private boolean received = false;
                @Override
                public void onOpen(ServerHandshake h) {
                    System.out.println("Соединение установлено для " + cleanSymbol);
                }
                @Override
                public void onMessage(String message) {
                    if (received) return;
                    try {
                        JSONObject json = new JSONObject(message);
                        if (json.has("k")) {
                            received = true;
                            double price = json.getJSONObject("k").getDouble("c");

                            // Отправляем результат
                            callback.accept(price);

                            // Закрываем поток
                            this.close();
                        }
                    } catch (Exception e) {
                        System.err.println("Ошибка парсинга символа " + cleanSymbol + ": " + e.getMessage());
                    }
                }
                @Override
                public void onClose(int c, String r, boolean rem) {
                    System.out.println("Соединение закрыто для символа " + cleanSymbol + ": " + r);
                }
                @Override
                public void onError(Exception ex) {
                    System.err.println("Ошибка WebSocket для символа " + cleanSymbol + ": " + ex.getMessage());
                    ex.printStackTrace();
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Получаю BID и ASK в реальном времени (для спреда) для списка символов
    public void connectCombined(Map<String, Integer> symbols) {

        String streams = symbols.keySet().stream()
            .map(sym -> {
                // Теперь компилятор знает, что sym — это String
                String s = sym.toLowerCase().trim();
                // Если мы запускаем клиент специально для тиков
                if ("ticks".equalsIgnoreCase(this.timeframe)) {
                    return String.format("%s@bookTicker", s);
                }
                // Для обычных таймфреймов (15m, 1h, 1d) подписываемся ТОЛЬКО на свечи kline!
                return String.format("%s@kline_%s", s, this.timeframe.toLowerCase());
                //return String.format("%s@kline_%s/%s@bookTicker", s, this.timeframe.toLowerCase(), s);
            })
            .collect(Collectors.joining("/"));

        String url = "wss://stream.binance.com:9443/stream?streams=" + streams;

        try {
            System.out.println("Подключение к Combined Stream: " + url);
            webSocketClient = new WebSocketClient(new URI(url)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("WebSocket открыт. Подписки: " + symbols);
                }
                @Override
                public void onMessage(String message) {
                    try {
                        JSONObject json = new JSONObject(message);
                        if (!json.has("stream") || !json.has("data")) return;

                        String streamName = json.getString("stream");
                        JSONObject data = json.getJSONObject("data");
                        String currentSymbol = data.getString("s"); // Например: "BTCUSDT"

                        // Достаем ID один раз для обоих условий
                        Integer id = symbols.get(currentSymbol.toUpperCase());
                        long symbolId = (id != null) ? id.longValue() : 0L;

                        // Так как подключаюсь к Combined Stream где есть и свечи и тикеры, то нужно их обрабатывать отдельно
                        // --- БЛОК СВЕЧЕЙ ---
                        if (streamName.contains("@kline")) {
                            if (data.has("k")) {
                                JSONObject k = data.getJSONObject("k");
                                Candle candle = new Candle();

                                candle.setExchangeId(1);
                                candle.setSymbolId(symbolId);
                                candle.setSymbol(currentSymbol);
                                candle.setType("CANDLE"); // тип для свечи должен быть CANDLE
                                candle.setTimeframe(timeframe);

                                candle.setOpenTime(k.getLong("t"));
                                candle.setCloseTime(k.getLong("T"));
                                candle.setOpen(k.getBigDecimal("o"));
                                candle.setHigh(k.getBigDecimal("h"));
                                candle.setLow(k.getBigDecimal("l"));
                                candle.setClose(k.getBigDecimal("c"));
                                candle.setVolume(k.getBigDecimal("v"));
                                candle.setIsClosed(k.getBoolean("x"));

                                // Для kline вызываем метод отправки свечи
                                candlePublisher.publishCandle(candle);
                            }
                        }
                        // --- БЛОК ТИКОВ (BID/ASK) ---
                        else if (streamName.contains("@bookTicker")) {
                            double bidPrice = data.getDouble("b");
                            double askPrice = data.getDouble("a");

                            // Вызываем publishTick только здесь, используя полученные данные
                            candlePublisher.publishTick(currentSymbol, symbolId, bidPrice, askPrice);

                            // Лог (можно убрать, если слишком много сообщений)
                            //System.out.println(String.format("[%s] TICK -> Bid: %.4f | Ask: %.4f", currentSymbol, bidPrice, askPrice));
                        }
                    } catch (Exception e) {
                        System.err.println("Ошибка обработки сообщения: " + e.getMessage());
                    }
                }
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("Соединение закрыто: " + reason + ". Попытка переподключения через 5 сек...");
                    new Thread(() -> {
                        try {
                            Thread.sleep(5000);
                            connectCombined(symbols); // Рекурсивный вызов для переподключения
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
                @Override
                public void onError(Exception ex) {
                    System.err.println("Ошибка WebSocket: " + ex.getMessage());
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Метод для прогрева индикаторов при запуске приложения.
    // Принимает множество символов, для которых нужно загрузить историю и прогреть индикаторы
    public void warmUpAll(Map<String, Integer> symbols) {
        for (Map.Entry<String, Integer> entry : symbols.entrySet()) {
            String sym = entry.getKey();
            Integer id = entry.getValue();

            // 1. Загружаем историю свечей через ваш метод REST API
            // Предполагаем, что fetchHistoryFromRest возвращает List<Candle>
            List<Candle> historicalCandles = fetchHistoryFromRest(sym, this.timeframe, 100);

            // 6. ПЕРЕДАЕМ ту же историю в слушатель (для сигналов)
            for (Candle c : historicalCandles) {
                c.setSymbolId(id.longValue());
                c.setSymbol(sym);
                c.setTimeframe(this.timeframe); // ОБЯЗАТЕЛЬНО установите таймфрейм
                c.setType("HISTORY");

                System.out.println("Отправка истории: " + c.getSymbol() + " timeframe: " + this.timeframe + " Price: " + c.getClose());
                candlePublisher.publishCandle(c);
            }

            System.out.println("Прогрев завершен для: " + sym + " [" + timeframe + "]");
        }
    }

    // Метод для получения исторических данных через REST API Binance
    // Параметры: symbol (например, "BTCUSDT"), interval (например, "1m"), limit (кол-во свечей)
    // Используется для начального прогрева индикаторов при запуске приложения
    private List<Candle> fetchHistoryFromRest(String symbol, String interval, int limit) {
        List<Candle> candles = new ArrayList<>();
        // Binance REST API URL
        String url = String.format("https://api.binance.com/api/v3/klines?symbol=%s&interval=%s&limit=%d",
            symbol.toUpperCase(), interval, limit
        );

        try {
            RestTemplate restTemplate = new RestTemplate();
            String response = restTemplate.getForObject(url, String.class);

            if (response == null) return candles;

            JSONArray jsonArray = new JSONArray(response);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONArray k = jsonArray.getJSONArray(i);
                Candle candle = new Candle();

                candle.setSymbol(symbol);
                candle.setOpenTime(k.getLong(0));
                candle.setOpen(new BigDecimal(k.getString(1)));
                candle.setHigh(new BigDecimal(k.getString(2)));
                candle.setLow(new BigDecimal(k.getString(3)));
                candle.setClose(new BigDecimal(k.getString(4)));
                candle.setVolume(new BigDecimal(k.getString(5)));
                candle.setCloseTime(k.getLong(6));
                candle.setIsClosed(true);

                candles.add(candle);
            }

        } catch (Exception e) {
            System.err.println("Ошибка REST-запроса к Binance (" + symbol + "): " + e.getMessage());
        }
        return candles;
    }
}

/*
// Пример запуска
// 1. Получаем экземпляр менеджера (он будет принимать данные из сокета)
BacktestManager manager = BacktestManager.getInstance();
// 2. Создаем сокет, передавая ему менеджер
// (Убедитесь, что вы обновили конструктор сокета, как мы обсуждали ранее)
BinanceKlineWebSocket ws = new BinanceKlineWebSocket(manager);
// 3. Подключаемся к паре BTCUSDT на минутном таймфрейме
ws.connect("btcusdt", "1m");
System.out.println("Поток котировок запущен...");
*/

