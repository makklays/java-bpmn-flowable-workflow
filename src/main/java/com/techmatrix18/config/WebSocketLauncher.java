package com.techmatrix18.config;

import com.techmatrix18.clients.BinanceKlineWebSocket;
import com.techmatrix18.rabbitmq.CandlePublisher;
import com.techmatrix18.telegram.TelegramService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static java.util.Map.entry;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocketLauncher - класс для запуска WebSocket клиента Binance и публикации свечей в RabbitMQ.
 * А также для получения исторических данных и прогрева индикаторов.
 *
 * @author Alexander Kuziv <makklays@gmail.com>
 * @company TechMatrix18
 * @since 16.04.2026
 * @version 0.0.1
 */

@Configuration
public class WebSocketLauncher {

    private final TelegramService telegramService;

    public WebSocketLauncher(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    private final Map<String, Integer> symbols = Map.ofEntries(
        entry("BTCUSDT", 1),
        entry("ETHUSDT", 2),
        entry("BCHUSDT", 3),
        entry("XRPUSDT", 4),
        entry("LTCUSDT", 5),
        entry("TRXUSDT", 6),
        entry("ETCUSDT", 7),
        entry("LINKUSDT", 8),
        entry("XLMUSDT", 9),
        entry("ADAUSDT", 10),
        entry("DASHUSDT", 12),
        entry("ATOMUSDT", 16),
        entry("NEOUSDT", 21),
        entry("ALGOUSDT", 25),
        entry("COMPUSDT", 29),
        entry("SOLUSDT", 42)
    );

    //-- Spring Boot находит такие бины и выполняет их код сразу после полного запуска контекста приложения --

    @Bean
    public CommandLineRunner appInitializer(CandlePublisher publisher) {
        return args -> {
            System.out.println("====== 🚀 ЗАПУСК СИСТЕМЫ ИНИЦИАЛИЗАЦИИ РОБОТА ======");

            // ==========================================
            // ШАГ 1: ПОСЛЕДОВАТЕЛЬНЫЙ ПРОГРЕВ ИСТОРИИ
            // ==========================================
            try {
                // Прогрев D1
                System.out.println(">>> [1/3] Загрузка истории 1d...");
                BinanceKlineWebSocket clientD1 = new BinanceKlineWebSocket("BTC", 1, "1d", publisher);
                clientD1.warmUpAll(symbols);
                Thread.sleep(1000); // Небольшая пауза, чтобы не спамить API Binance

                // Прогрев H1
                System.out.println(">>> [2/3] Загрузка истории 1h...");
                BinanceKlineWebSocket clientH1 = new BinanceKlineWebSocket("BTC", 1, "1h", publisher);
                clientH1.warmUpAll(symbols);
                Thread.sleep(1000);

                // Прогрев M15
                System.out.println(">>> [3/3] Загрузка истории 15m...");
                BinanceKlineWebSocket clientM15 = new BinanceKlineWebSocket("BTC", 1, "15m", publisher);
                clientM15.warmUpAll(symbols);
                Thread.sleep(1000);

                System.out.println("✅ ИСТОРИЯ ВСЕХ ТАЙМФРЕЙМОВ УСПЕШНО ЗАГРУЖЕНА И ИНДИКАТОРЫ ПРОГРЕТЫ.");
            } catch (Exception e) {
                System.err.println("❌ КРИТИЧЕСКАЯ ОШИБКА ПРИ ПРОГРЕВЕ ИСТОРИИ: " + e.getMessage());
                // Решите, тушить ли приложение при ошибке прогрева:
                // System.exit(1);
            }

            // ==========================================
            // ШАГ 2: ЗАПУСК ЖИВЫХ ВЕБ-СОКЕТОВ (ONLINE)
            // ==========================================
            System.out.println("====== ⚡ ВКЛЮЧЕНИЕ ЖИВОГО ПОТОКА ДАННЫХ (WEBSOCKETS) ======");

            // 1. Запуск комбинированного потока для ВСЕХ монет и таймфрейма 1d
            new BinanceKlineWebSocket("BTC", 1, "1d", publisher).connectCombined(symbols);
            System.out.println(">>> Живой поток 1d подключен.");

            // 2. Запуск комбинированного потока для ВСЕХ монет и таймфрейма 1h
            new BinanceKlineWebSocket("BTC", 1, "1h", publisher).connectCombined(symbols);
            System.out.println(">>> Живой поток 1h подключен.");

            // 3. Запуск комбинированного потока для ВСЕХ монет и таймфрейма 15m
            new BinanceKlineWebSocket("BTC", 1, "15m", publisher).connectCombined(symbols);
            System.out.println(">>> Живой поток 15m подключен.");

            // 4. Запуск отдельных 1-минутных (1m) потоков для ВСЕХ монет
            // (из метода startup ниже - перенесен сюда для последовательного запуска из одного метода)
            symbols.forEach((symbol, id) -> {
                CompletableFuture.runAsync(() -> {
                    try {
                        BinanceKlineWebSocket clientM1 = new BinanceKlineWebSocket(symbol, id, "1m", publisher);
                        clientM1.connect(symbol, "1m");
                    } catch (Exception e) {
                        System.err.println("Ошибка в сокете 1m для " + symbol + ": " + e.getMessage());
                    }
                });
            });
            System.out.println(">>> Живые потоки 1m запущены в асинхронных фоновых потоках.");

            // 5. Запуск комбинированного потока для ВСЕХ монет --> для ТИКОВ (Bid/Ask)
            // (Используем метод, настроенный под bookTicker стрим)
            // Единственный и чистый поток ТИКОВ (передаем ключевое слово "ticks" вместо "1m")
            BinanceKlineWebSocket tickClient = new BinanceKlineWebSocket("BTCUSDT", 1, "ticks", publisher);
            tickClient.connectCombined(symbols);
            System.out.println(">>> Единый комбинированный поток ТИКОВ (Bid/Ask) успешно активирован.");

            System.out.println("====== 🎉 РОБОТ ПОЛНОСТЬЮ ГОТОВ К РАБОТЕ ======");

            // ==========================================
            // ШАГ 3: УВЕДОМЛЕНИЕ В ТЕЛЕГРАМ О ГОТОВНОСТИ
            // ==========================================
            String startMsg = String.format(
                    "🚀 *System successfully launched!*\n\n" +
                    "📊 Coins actively trading: *%d*\n" +
                    "✅ Indicators for `15m`, `1h`, and `1d` timeframes are fully warmed up.\n" +
                    "⚡ All live WebSocket streams are connected.\n\n" +
                    "🤖 Trading bot is ready to generate signals!",
                    symbols.size()
            );

            // Вызываем ваш сервис телеграма (убедитесь, что внедряете его в этот класс или используете статический метод)
            telegramService.sendMessageForAll(startMsg);

            System.out.println("====== 🎉 РОБОТ ПОЛНОСТЬЮ ГОТОВ К РАБОТЕ ======");
        };
    }

    // Если монет < 10 - можно запускать по одной в отдельных потоках.
    // Получаю 1 минутные свечи для каждой монеты
    /*@Bean
    public CommandLineRunner startup(CandlePublisher candlePublisher) {
        return args -> {
            System.out.println("🚀 Инициализация WebSocket для СВЕЧЕЙ (Klines)...");
            // Список монет из переменной
            // TODO: вынести в конфиг или базу, чтобы не менять код при добавлении монет
            symbols.forEach((symbol, id) -> {
                CompletableFuture.runAsync(() -> {
                    try {
                        BinanceKlineWebSocket client = new BinanceKlineWebSocket(symbol, id, "1m", candlePublisher);
                        client.connect(symbol, "1m");
                        System.out.println(">>> Запущен поток Klines для: " + symbol);
                    } catch (Exception e) {
                        System.err.println("Ошибка в сокете Klines для " + symbol + ": " + e.getMessage());
                    }
                });
            });
            System.out.println(">>> Все WebSocket подключения инициированы в фоновых потоках");
        };
    }

    // Один клиент для всех монет (через комбинированный поток) - лучше для большого количества монет.
    // Получаю bid и ask для списка монет
    @Bean
    public CommandLineRunner startupCombined(CandlePublisher candlePublisher) {
        return args -> {
            System.out.println("🚀 Инициализация единого WebSocket для ТИКОВ (Bid/Ask)...");
            try {
                // 1. Берем любую монету как "стартовую" (для конструктора)
                String firstSymbol = symbols.keySet().iterator().next();

                // 2. Создаем ОДИН клиент
                // ВАЖНО: передайте в конструктор или метод саму карту 'coins',
                // чтобы внутри onMessage вы могли делать: candle.setSymbolId(coins.get(currentSymbol))
                // TODO: переделать конструктор, чтобы он был универсальным для обеих функций
                BinanceKlineWebSocket client = new BinanceKlineWebSocket(firstSymbol, 1, "1m", candlePublisher);

                // 3. Запускаем ОДНО комбинированное соединение для всех монет сразу
                client.connectCombined(symbols);
                System.out.println(">>> Комбинированный поток ТИКОВ успешно запущен для всех монет.");
            } catch (Exception e) {
                System.err.println("Ошибка запуска комбинированного потока тиков: " + e.getMessage());
            }
        };
    }

    // ПРОГРЕВ ИСТОРИИ ДЛЯ ВСЕХ МОНЕТ ПЕРЕД ЗАПУСКОМ WEBSOCKET - 15m
    // важно для корректной работы индикаторов с самого начала
    @Bean
    public CommandLineRunner startupM15(CandlePublisher publisher) {
        return args -> {
            BinanceKlineWebSocket clientM15 = new BinanceKlineWebSocket("BTC", 1, "15m", publisher);
            System.out.println(">>> Инициализация индикаторов 15m: загрузка истории для " + symbols.size() + " символа...");
            clientM15.warmUpAll(symbols); // Прогреет M15 и публикация исторические данные в RabbitMQ для всех монет
            System.out.println(">>> Индикаторы прогреты 15m. Запуск WebSocket...");
            clientM15.connectCombined(symbols); // Слушает M15 и публикует онлайн в RabbitMQ для всех монет
        };
    }

    // ПРОГРЕВ ИСТОРИИ ДЛЯ ВСЕХ МОНЕТ ПЕРЕД ЗАПУСКОМ WEBSOCKET - 1h
    // важно для корректной работы индикаторов с самого начала
    @Bean
    public CommandLineRunner startupH1(CandlePublisher publisher) {
        return args -> {
            BinanceKlineWebSocket clientH1 = new BinanceKlineWebSocket("BTC", 1, "1h", publisher);
            System.out.println(">>> Инициализация индикаторов 1h: загрузка истории для " + symbols.size() + " символа...");
            clientH1.warmUpAll(symbols); // Прогреет H1 и публикация исторические данные в RabbitMQ для всех монет
            System.out.println(">>> Индикаторы прогреты 1h. Запуск WebSocket...");
            clientH1.connectCombined(symbols); // Слушает H1 и публикует онлайн в RabbitMQ для всех монет
        };
    }

    // ПРОГРЕВ ИСТОРИИ ДЛЯ ВСЕХ МОНЕТ ПЕРЕД ЗАПУСКОМ WEBSOCKET - 1d
    // важно для корректной работы индикаторов с самого начала
    @Bean
    public CommandLineRunner startupD1(CandlePublisher publisher) {
        return args -> {
            BinanceKlineWebSocket clientD1 = new BinanceKlineWebSocket("BTC", 1, "1d", publisher);
            System.out.println(">>> Инициализация индикаторов 1d: загрузка истории для " + symbols.size() + " символа...");
            clientD1.warmUpAll(symbols); // Прогреет D1 и публикация исторические данные в RabbitMQ для всех монет
            System.out.println(">>> Индикаторы прогреты 1d. Запуск WebSocket...");
            clientD1.connectCombined(symbols); // Слушает D1 и публикует онлайн в RabbitMQ для всех монет
        };
    }*/

    // Если монет > 20
    /*@Bean
    public CommandLineRunner startup(CandlePublisher candlePublisher) {
        return args -> {
            List<String> symbols = List.of("BTCUSDT", "ETHUSDT", "SOLUSDT");

            for (String symbol : symbols) {
                // Создаем и подключаем
                BinanceKlineWebSocket client = new BinanceKlineWebSocket(symbol, 1, "1m", candlePublisher);
                client.connect(symbol, "1m");
            }

            System.out.println(">>> Запущено потоков: " + symbols.size());
        };
    }*/
}

