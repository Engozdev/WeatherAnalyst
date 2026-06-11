package org.practice.etl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.practice.model.WeatherRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DataTransformer {
    private static final Logger logger = LoggerFactory.getLogger(DataTransformer.class);
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter dateFormatter;

    public DataTransformer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    }

    /**
     * Основной метод трансформации: парсит JSON, очищает данные и собирает список записей
     */
    public List<WeatherRecord> transform(String rawJson, int cityId) {
        List<WeatherRecord> records = new ArrayList<>();

        if (rawJson == null || rawJson.isBlank()) {
            logger.error("Передана пустая строка JSON для трансформации. City ID: {}", cityId);
            return records;
        }

        try {
            // Десериализуем JSON в промежуточный DTO
            OpenMeteoDto dto = objectMapper.readValue(rawJson, OpenMeteoDto.class);

            if (dto.getHourly() == null || dto.getHourly().getTime() == null) {
                logger.warn("В полученном JSON отсутствуют почасовые данные. City ID: {}", cityId);
                return records;
            }

            HourlyData hourly = dto.getHourly();
            int totalRecords = hourly.getTime().size();
            logger.info("Начало обработки {} записей для города с ID: {}", totalRecords, cityId);

            // Переменные для хранения последних валидных значений для обработки null-пропусков
            Double lastValidTemp = 0.0;
            Integer lastValidHumidity = 60;
            Double lastValidPressure = 1013.2;
            Double lastValidWindSpeed = 0.0;
            Double lastValidPrecipitation = 0.0;
            Integer lastValidCloudCover = 0;

            // Итерируемся по параллельным массивам JSON и билдим объекты WeatherRecord
            for (int i = 0; i < totalRecords; i++) {
                try {
                    String rawTime = hourly.getTime().get(i);
                    LocalDateTime timestamp = LocalDateTime.parse(rawTime, dateFormatter);

                    Double temp = validateAndImpute(
                            hourly.getTemperature().get(i), -60.0, 60.0, lastValidTemp, "Температура", rawTime);
                    lastValidTemp = temp;

                    Integer humidity = validateAndImpute(
                            hourly.getHumidity().get(i), 0, 100, lastValidHumidity, "Влажность", rawTime);
                    lastValidHumidity = humidity;

                    Double pressure = validateAndImpute(
                            hourly.getPressure().get(i), 800.0, 1100.0, lastValidPressure, "Давление", rawTime);
                    lastValidPressure = pressure;

                    Double windSpeed = validateAndImpute(
                            hourly.getWindSpeed().get(i), 0.0, 150.0, lastValidWindSpeed, "Скорость ветра", rawTime);
                    lastValidWindSpeed = windSpeed;

                    Double precipitation = validateAndImpute(
                            hourly.getPrecipitation().get(i), 0.0, 300.0, lastValidPrecipitation, "Осадки", rawTime);
                    lastValidPrecipitation = precipitation;

                    Integer cloudCover = validateAndImpute(
                            hourly.getCloudCover().get(i), 0, 100, lastValidCloudCover, "Облачность", rawTime);
                    lastValidCloudCover = cloudCover;

                    WeatherRecord record = WeatherRecord.builder()
                            .cityId(cityId)
                            .timestamp(timestamp)
                            .temperature(temp)
                            .humidity(humidity)
                            .pressure(pressure)
                            .windSpeed(windSpeed)
                            .precipitation(precipitation)
                            .cloudCover(cloudCover)
                            .build();

                    records.add(record);

                } catch (Exception e) {
                    logger.error("Ошибка при обработке строки под индексом {}: {}", i, e.getMessage());
                }
            }

            logger.info("Трансформация успешно завершена. Подготовлено {} чистых записей.", records.size());

        } catch (Exception e) {
            logger.error("Критическая ошибка парсинга структуры JSON: {}", e.getMessage(), e);
        }

        return records;
    }


    // Универсальный метод валидации и восстановления данных для любых числовых типов.
    private <T extends Number & Comparable<T>> T validateAndImpute(
            T value, T min, T max, T fallback, String metricName, String time) {

        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            logger.warn("Аномалия или пропуск в метрике '{}' на дату {}. Значение: {}. Заменено на: {}",
                    metricName, time, value, fallback);
            return fallback;
        }
        return value;
    }

    // Внутренние DTO-классы для точного маппинга JSON-ответа
    @Data
    private static class OpenMeteoDto {
        private HourlyData hourly;
    }

    @Data
    private static class HourlyData {
        private List<String> time;
        @JsonProperty("temperature_2m")
        private List<Double> temperature;
        @JsonProperty("relative_humidity_2m")
        private List<Integer> humidity;
        @JsonProperty("pressure_msl")
        private List<Double> pressure;
        @JsonProperty("wind_speed_10m")
        private List<Double> windSpeed;
        private List<Double> precipitation;
        @JsonProperty("cloud_cover")
        private List<Integer> cloudCover;
    }
}