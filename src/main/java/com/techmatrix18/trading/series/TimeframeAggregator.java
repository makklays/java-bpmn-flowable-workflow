package com.techmatrix18.trading.series;

import com.techmatrix18.model.Candle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Модуль агрегации свечей для торгового движка.
 * Позволяет пересчитывать базовые свечи (например, 1-минутные) в любые старшие таймфреймы.
 *
 * @author Alexander Kuziv
 * @since 16.09.2026
 * @company TechMatrix18
 * @version 0.1.0
 */

@Component
public class TimeframeAggregator {

    /**
     * Выполняет высокоскоростную линейную агрегацию свечей в выбранный таймфрейм.
     * Расчет происходит за один проход O(N) без создания промежуточных объектов в цикле.
     *
     * @param candles   Исходный список свечей младшего таймфрейма (например, 1m)
     * @param timeframe Целевой таймфрейм (например, "5m", "15m", "1h", "1d")
     * @return Новый список агрегированных свечей старшего таймфрейма
     */
    public List<Candle> aggregate(List<Candle> candles, String timeframe) {
        if (candles == null || candles.isEmpty()) {
            return new ArrayList<>();
        }
        // Если запрашиваемый таймфрейм совпадает с базовым, возвращаем исходный список
        if ("1m".equalsIgnoreCase(timeframe)) {
            return candles;
        }

        // Получаем точный и защищенный от переполнения интервал в миллисекундах
        long intervalMs = parseTimeframeToMs(timeframe);

        List<Candle> result = new ArrayList<>();

        // Инициализируем стартовую точку первой свечи
        Candle firstCandle = candles.get(0);
        long currentIntervalStart = (firstCandle.getOpenTime() / intervalMs) * intervalMs;

        // Локальные переменные для сборки текущей крупной свечи (работают быстрее полей объектов)
        BigDecimal open = firstCandle.getOpen();
        BigDecimal close = firstCandle.getClose();
        BigDecimal high = firstCandle.getHigh();
        BigDecimal low = firstCandle.getLow();
        BigDecimal volume = firstCandle.getVolume() != null ? firstCandle.getVolume() : BigDecimal.ZERO;
        String symbol = firstCandle.getSymbol();

        // Линейный проход со 2-й свечи до конца списка (Сложность строго O(N))
        for (int i = 1; i < candles.size(); i++) {
            Candle c = candles.get(i);
            long candleIntervalStart = (c.getOpenTime() / intervalMs) * intervalMs;

            if (candleIntervalStart == currentIntervalStart) {
                // Свеча входит в текущую временную корзину — обновляем экстремумы и цену Close
                close = c.getClose(); // Итоговым останется Close самой последней свечи периода

                if (c.getHigh().compareTo(high) > 0) high = c.getHigh();
                if (c.getLow().compareTo(low) < 0) low = c.getLow();
                if (c.getVolume() != null) volume = volume.add(c.getVolume());
            } else {
                // Граница таймфрейма пройдена! Фиксируем собранную крупную свечу
                Candle combined = new Candle();
                combined.setOpenTime(currentIntervalStart);
                combined.setSymbol(symbol);
                combined.setOpen(open);
                combined.setClose(close);
                combined.setHigh(high);
                combined.setLow(low);
                combined.setVolume(volume);
                result.add(combined);

                // Открываем новую временную корзину и инициализируем её данными текущей свечи 'c'
                currentIntervalStart = candleIntervalStart;
                open = c.getOpen();
                close = c.getClose();
                high = c.getHigh();
                low = c.getLow();
                volume = c.getVolume() != null ? c.getVolume() : BigDecimal.ZERO;
            }
        }

        // Важно: сохраняем последнюю накопленную свечу после выхода из цикла
        Candle combined = new Candle();
        combined.setOpenTime(currentIntervalStart);
        combined.setSymbol(symbol);
        combined.setOpen(open);
        combined.setClose(close);
        combined.setHigh(high);
        combined.setLow(low);
        combined.setVolume(volume);
        result.add(combined);

        return result;
    }

    /**
     * Безопасно парсит строку таймфрейма в миллисекунды.
     * Использование суффикса L (long) гарантирует защиту от арифметического переполнения.
     */
    private long parseTimeframeToMs(String timeframe) {
        if (timeframe == null || timeframe.isEmpty()) {
            return 60 * 1000L; // Дефолт: 1 минута в миллисекундах
        }

        try {
            // Очищаем строку от букв для получения числа
            long value = Long.parseLong(timeframe.replaceAll("[^0-9]", ""));
            String unit = timeframe.toLowerCase().replaceAll("[0-9]", "").trim();

            // Переводим в миллисекунды в типе long (поддерживает w - недели, d - дни, h - часы)
            return switch (unit) {
                case "h" -> value * 60 * 60 * 1000L;          // Часы в мс
                case "d" -> value * 24 * 60 * 60 * 1000L;     // Дни в мс
                case "w" -> value * 7 * 24 * 60 * 60 * 1000L; // Недели в мс
                default  -> value * 60 * 1000L;               // По умолчанию: минуты (m) в мс
            };
        } catch (Exception e) {
            return 60 * 1000L; // При любой ошибке парсинга откатываемся на безопасный дефолт в 1м
        }
    }
}

