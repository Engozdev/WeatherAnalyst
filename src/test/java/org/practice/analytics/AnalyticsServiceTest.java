package org.practice.analytics;

import org.practice.config.DatabaseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyticsServiceTest {
    private AnalyticsService analyticsService;
    private AnomalyDetector anomalyDetector;

    private final int TEST_CITY_ID = 999;
    private final int TEST_CITY_ID_2 = 888;

    @BeforeEach
    public void setUp() throws SQLException {
        analyticsService = new AnalyticsService();
        anomalyDetector = new AnomalyDetector();

        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(true);

            String insertCity = "INSERT INTO cities (id, name, latitude, longitude) " +
                    "VALUES (?, ?, 55.0, 35.0) " +
                    "ON CONFLICT (id) DO NOTHING";
            try (PreparedStatement ps = connection.prepareStatement(insertCity)) {
                ps.setInt(1, TEST_CITY_ID);
                ps.setString(2, "Тест-Город 1");
                ps.addBatch();

                ps.setInt(1, TEST_CITY_ID_2);
                ps.setString(2, "Тест-Город 2");
                ps.addBatch();

                ps.executeBatch();
            }

            // Очищаем старые тестовые записи
            try (PreparedStatement ps = connection
                    .prepareStatement("DELETE FROM weather_records WHERE city_id IN (?, ?)")) {
                ps.setInt(1, TEST_CITY_ID);
                ps.setInt(2, TEST_CITY_ID_2);
                ps.executeUpdate();
            }

            // Заливаем контролируемый массив данных
            String insertSql = "INSERT INTO weather_records (city_id, timestamp, temperature, humidity, pressure, wind_speed, precipitation, cloud_cover) "
                    +
                    "VALUES (?, CAST(? AS TIMESTAMP), ?, 70, 1013, ?, ?, 50)";

            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                // Исторические данные за прошлые годы по температуре
                insertRow(ps, TEST_CITY_ID, "2024-07-01 12:00:00", 20.0, 3.0, 1.0);
                insertRow(ps, TEST_CITY_ID, "2023-07-01 12:00:00", 20.0, 3.0, 1.0);
                insertRow(ps, TEST_CITY_ID, "2022-07-01 12:00:00", 20.0, 3.0, 1.0);
                insertRow(ps, TEST_CITY_ID, "2021-07-01 12:00:00", 20.0, 3.0, 1.0);

                // Целевые данные за 2025 год июль для TEST_CITY_ID
                insertRow(ps, TEST_CITY_ID, "2025-07-01 12:00:00", 20.0, 3.0, 1.0);
                insertRow(ps, TEST_CITY_ID, "2025-07-02 12:00:00", 21.0, 3.0, 1.0);
                insertRow(ps, TEST_CITY_ID, "2025-07-03 12:00:00", 19.0, 3.0, 1.0);
                insertRow(ps, TEST_CITY_ID, "2025-07-04 12:00:00", 38.0, 3.0, 1.0); // Жара
                insertRow(ps, TEST_CITY_ID, "2025-07-05 12:00:00", 17.0, 25.0, 50.0); // Ливень + штормовой ветер

                // Данные для проверки резкого падения температуры
                insertRow(ps, TEST_CITY_ID, "2025-07-06 12:00:00", 30.0, 3.0, 0.0);
                insertRow(ps, TEST_CITY_ID, "2025-07-07 12:00:00", 15.0, 3.0, 0.0); // Падение на 15 градусов

                // Данные для сезонного анализа
                insertRow(ps, TEST_CITY_ID, "2025-01-15 12:00:00", -5.0, 3.0, 0.0); // Зима
                insertRow(ps, TEST_CITY_ID, "2025-04-15 12:00:00", 10.0, 3.0, 0.0); // Весна
                insertRow(ps, TEST_CITY_ID, "2025-10-15 12:00:00", 8.0, 3.0, 0.0); // Осень

                // Данные для проверки сухих периодов - 5 дней без осадков
                insertRow(ps, TEST_CITY_ID, "2025-08-01 12:00:00", 22.0, 3.0, 0.0);
                insertRow(ps, TEST_CITY_ID, "2025-08-02 12:00:00", 22.0, 3.0, 0.0);
                insertRow(ps, TEST_CITY_ID, "2025-08-03 12:00:00", 22.0, 3.0, 0.0);
                insertRow(ps, TEST_CITY_ID, "2025-08-04 12:00:00", 22.0, 3.0, 0.0);
                insertRow(ps, TEST_CITY_ID, "2025-08-05 12:00:00", 22.0, 3.0, 0.0);

                // Данные для TEST_CITY_ID_2 для сравнения средней температуры
                insertRow(ps, TEST_CITY_ID_2, "2025-07-01 12:00:00", 25.0, 3.0, 0.0);
                insertRow(ps, TEST_CITY_ID_2, "2025-07-02 12:00:00", 26.0, 3.0, 0.0);
                insertRow(ps, TEST_CITY_ID_2, "2025-07-03 12:00:00", 24.0, 3.0, 0.0);

                ps.executeBatch();
            }
        }
    }

    private void insertRow(PreparedStatement ps, int cityId, String timestamp, double temp, double windSpeed,
            double precip) throws SQLException {
        ps.setInt(1, cityId);
        ps.setString(2, timestamp);
        ps.setDouble(3, temp);
        ps.setDouble(4, windSpeed);
        ps.setDouble(5, precip);
        ps.addBatch();
    }

    @AfterEach
    public void tearDown() throws SQLException {
        // удаляем все следы тестовых городов
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(true);

            try (PreparedStatement ps = connection
                    .prepareStatement("DELETE FROM weather_records WHERE city_id IN (?, ?)")) {
                ps.setInt(1, TEST_CITY_ID);
                ps.setInt(2, TEST_CITY_ID_2);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM cities WHERE id IN (?, ?)")) {
                ps.setInt(1, TEST_CITY_ID);
                ps.setInt(2, TEST_CITY_ID_2);
                ps.executeUpdate();
            }
        }
    }

    @Test
    public void testTemperatureAggregation() {
        Map<String, Double> stats = analyticsService.getYearlyTemperatureStats(TEST_CITY_ID, 2025);

        assertNotNull(stats);
        assertEquals(-5.0, stats.get("MIN"), 0.1);
        assertEquals(38.0, stats.get("MAX"), 0.1);
        assertEquals(18.87, stats.get("AVG"), 0.1);
    }

    @Test
    public void testHeavyPrecipitationCounting() {
        int heavyRainDays = analyticsService.getDaysWithHeavyPrecipitation(TEST_CITY_ID, 2025, 20.0);
        assertEquals(1, heavyRainDays);
    }

    @Test
    public void testAnomalyDetection() {
        List<String> anomalies = anomalyDetector.findTemperatureAnomalies(TEST_CITY_ID, 2025, 7);

        assertFalse(anomalies.isEmpty(), "Список аномалий не должен быть пустым");

        boolean foundTargetAnomaly = anomalies.stream()
                .anyMatch(log -> log.contains("2025-07-04") && log.contains("Аномальная ЖАРА") && log.contains("38"));

        assertTrue(foundTargetAnomaly, "Система должна была обнаружить аномальную жару 2025-07-04");
    }

    @Test
    public void testSeasonalAverageTemperature() {
        Map<String, Double> seasonalAvg = analyticsService.getSeasonalAverageTemperature(TEST_CITY_ID, 2025);
        assertNotNull(seasonalAvg);
        assertEquals(-5.0, seasonalAvg.get("WINTER"), 0.1);
        assertEquals(10.0, seasonalAvg.get("SPRING"), 0.1);
        assertEquals(22.5, seasonalAvg.get("SUMMER"), 0.1);
        assertEquals(8.0, seasonalAvg.get("AUTUMN"), 0.1);
    }

    @Test
    public void testAllTimeTemperatureStats() {
        Map<String, Double> stats = analyticsService.getAllTimeTemperatureStats(TEST_CITY_ID);
        assertNotNull(stats);
        assertEquals(-5.0, stats.get("MIN"), 0.1);
        assertEquals(38.0, stats.get("MAX"), 0.1);
        assertTrue(stats.get("AVG") > 10.0 && stats.get("AVG") < 25.0);
    }

    @Test
    public void testCompareCitiesAverageTemperature() {
        Map<String, Double> comparison = analyticsService.compareCitiesAverageTemperature(
                List.of(TEST_CITY_ID, TEST_CITY_ID_2), 2025);
        assertNotNull(comparison);
        assertEquals(25.0, comparison.get("Тест-Город 2"), 0.1);
        assertTrue(comparison.containsKey("Тест-Город 1"));
    }

    @Test
    public void testExtremeTemperaturePeriods() {
        // Теплые периоды
        List<Map<String, Object>> warmestDays = analyticsService.getExtremeTemperaturePeriods(TEST_CITY_ID, 2, true);
        assertEquals(2, warmestDays.size());
        assertEquals("2025-07-04", warmestDays.get(0).get("date")); // 38.0
        assertEquals("2025-07-06", warmestDays.get(1).get("date")); // 30.0

        // Холодные периоды
        List<Map<String, Object>> coldestDays = analyticsService.getExtremeTemperaturePeriods(TEST_CITY_ID, 1, false);
        assertEquals(1, coldestDays.size());
        assertEquals("2025-01-15", coldestDays.get(0).get("date")); // -5.0
    }

    @Test
    public void testSuddenTemperatureDrops() {
        List<String> drops = anomalyDetector.findSuddenTemperatureDrops(TEST_CITY_ID, 2025, 10.0);
        assertFalse(drops.isEmpty());
        assertTrue(drops.stream().anyMatch(d -> d.contains("2025-07-07") && d.contains("Падение: 15")));
    }

    @Test
    public void testAnomalousPrecipitation() {
        List<String> anomalies = anomalyDetector.findAnomalousPrecipitation(TEST_CITY_ID, 2025);
        assertFalse(anomalies.isEmpty());
        assertTrue(anomalies.stream().anyMatch(a -> a.contains("2025-07-05") && a.contains("50.0")));
    }

    @Test
    public void testAnomalousWindSpeed() {
        List<String> anomalies = anomalyDetector.findAnomalousWindSpeed(TEST_CITY_ID, 2025);
        assertFalse(anomalies.isEmpty());
        assertTrue(anomalies.stream().anyMatch(a -> a.contains("2025-07-05") && a.contains("25.0")));
    }

    @Test
    public void testDrySpells() {
        List<String> spells = anomalyDetector.findDrySpells(TEST_CITY_ID, 2025, 5);
        assertFalse(spells.isEmpty());
        assertTrue(spells.stream().anyMatch(s -> s.contains("с 2025-08-01 по 2025-08-05") && s.contains("5 дней")));
    }
}