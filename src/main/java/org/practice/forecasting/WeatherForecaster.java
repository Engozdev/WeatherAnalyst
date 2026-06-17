package org.practice.forecasting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.practice.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeatherForecaster {
    private static final Logger logger = LoggerFactory.getLogger(WeatherForecaster.class);
    private final ObjectMapper objectMapper;

    public WeatherForecaster() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Рассчитывает прогноз на 3 дня вперед на основе последних 14 дней наблюдений
     * и сохраняет его в таблицу analytics_results.
     */
    public String calculateAndSaveForecast(int cityId) throws SQLException {
        logger.info("Запуск расчета температурного прогноза для города ID: {}", cityId);
        List<DailyStats> history = fetchRecentDailyStats(cityId);
        logger.info("Загружено {} дней исторических данных для города ID: {}", history.size(), cityId);
        if (history.size() < 2) {
            throw new IllegalStateException(
                    "Недостаточно исторических данных для построения прогноза (требуется минимум 2 дня)");
        }

        // Вычисляем линейную регрессию
        int n = history.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;
        double totalSpread = 0;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = history.get(i).avgTemp;
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
            totalSpread += (history.get(i).maxTemp - history.get(i).minTemp);
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;
        double avgSpread = totalSpread / n;
        logger.debug("Параметры регрессии: наклон = {}, свободный член = {}, средний разброс = {}", slope, intercept,
                avgSpread);

        // Определение тренда
        String trend;
        if (slope > 0.1) {
            trend = "Потепление";
        } else if (slope < -0.1) {
            trend = "Похолодание";
        } else {
            trend = "Стабильно";
        }
        logger.info("Выявленный температурный тренд: {}", trend);

        // Построение прогноза на 3 дня
        LocalDate lastDate = history.get(n - 1).date;
        List<Map<String, Object>> forecastDays = new ArrayList<>();

        for (int d = 1; d <= 3; d++) {
            double projectedAvg = slope * (n - 1 + d) + intercept;
            double projectedMin = projectedAvg - avgSpread / 2.0;
            double projectedMax = projectedAvg + avgSpread / 2.0;

            Map<String, Object> dayForecast = new HashMap<>();
            dayForecast.put("day", d);
            dayForecast.put("date", lastDate.plusDays(d).toString());
            dayForecast.put("avg_temp", Math.round(projectedAvg * 10.0) / 10.0);
            dayForecast.put("min_temp", Math.round(projectedMin * 10.0) / 10.0);
            dayForecast.put("max_temp", Math.round(projectedMax * 10.0) / 10.0);
            forecastDays.add(dayForecast);

            logger.debug("Спрогнозирован день {}: дата = {}, ср. темп = {}°C, диапазон = [{}°C, {}°C]",
                    d, dayForecast.get("date"), dayForecast.get("avg_temp"), dayForecast.get("min_temp"),
                    dayForecast.get("max_temp"));
        }

        Map<String, Object> forecastResult = new HashMap<>();
        forecastResult.put("trend", trend);
        forecastResult.put("forecast", forecastDays);

        String jsonResult;
        try {
            jsonResult = objectMapper.writeValueAsString(forecastResult);
        } catch (Exception e) {
            logger.error("Ошибка сериализации прогноза в JSON", e);
            throw new RuntimeException("Ошибка при сериализации прогноза в JSON", e);
        }

        // Сохранение в бд
        logger.info("Сохранение результатов прогноза в таблицу analytics_results для города ID: {}", cityId);
        String sql = """
                INSERT INTO analytics_results (city_id, record_type, metric_name, result_data) \
                VALUES (?, 'FORECAST', 'TEMPERATURE', CAST(? AS JSONB))""";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setString(2, jsonResult);
            ps.executeUpdate();
        }
        logger.info("Прогноз успешно сохранен для города ID: {}", cityId);

        return jsonResult;
    }

    private List<DailyStats> fetchRecentDailyStats(int cityId) throws SQLException {
        String sql = """
                SELECT DATE(timestamp) as obs_date, \
                       AVG(temperature) as avg_temp, \
                       MAX(temperature) as max_temp, \
                       MIN(temperature) as min_temp \
                FROM weather_records \
                WHERE city_id = ? \
                GROUP BY obs_date \
                ORDER BY obs_date DESC \
                LIMIT 14""";

        List<DailyStats> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DailyStats(
                            rs.getDate("obs_date").toLocalDate(),
                            rs.getDouble("avg_temp"),
                            rs.getDouble("max_temp"),
                            rs.getDouble("min_temp")));
                }
            }
        }
        // Переворачиваем, чтобы упорядочить хронологически
        Collections.reverse(list);
        return list;
    }

    private static class DailyStats {
        LocalDate date;
        double avgTemp;
        double maxTemp;
        double minTemp;

        DailyStats(LocalDate date, double avgTemp, double maxTemp, double minTemp) {
            this.date = date;
            this.avgTemp = avgTemp;
            this.maxTemp = maxTemp;
            this.minTemp = minTemp;
        }
    }
}
