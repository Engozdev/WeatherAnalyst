package org.practice.forecasting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.practice.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

public class WeatherForecasterTest {
    private WeatherForecaster weatherForecaster;
    private final int TEST_CITY_ID = 777;

    @BeforeEach
    public void setUp() throws SQLException {
        weatherForecaster = new WeatherForecaster();

        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(true);

            String insertCity = "INSERT INTO cities (id, name, latitude, longitude) " +
                    "VALUES (?, 'Тест-Прогноз-Город', 55.0, 35.0) " +
                    "ON CONFLICT (id) DO NOTHING";
            try (PreparedStatement ps = connection.prepareStatement(insertCity)) {
                ps.setInt(1, TEST_CITY_ID);
                ps.executeUpdate();
            }

            cleanupDb(connection);

            // Заливаем 14 дней с четким трендом потепления от 11°C до 24°C
            String insertSql = "INSERT INTO weather_records (city_id, timestamp, temperature, humidity, pressure, wind_speed, precipitation, cloud_cover) "
                    +
                    "VALUES (?, CAST(? AS TIMESTAMP), ?, 70, 1013, 3, 0.0, 50)";

            try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                for (int i = 1; i <= 14; i++) {
                    String day = String.format("%02d", i);
                    double temp = 10.0 + i;
                    insertRow(ps, TEST_CITY_ID, "2025-06-" + day + " 12:00:00", temp);
                }
                ps.executeBatch();
            }
        }
    }

    private void insertRow(PreparedStatement ps, int cityId, String timestamp, double temp) throws SQLException {
        ps.setInt(1, cityId);
        ps.setString(2, timestamp);
        ps.setDouble(3, temp);
        ps.addBatch();
    }

    @AfterEach
    public void tearDown() throws SQLException {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(true);
            cleanupDb(connection);

            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM cities WHERE id = ?")) {
                ps.setInt(1, TEST_CITY_ID);
                ps.executeUpdate();
            }
        }
    }

    private void cleanupDb(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM weather_records WHERE city_id = ?")) {
            ps.setInt(1, TEST_CITY_ID);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM analytics_results WHERE city_id = ?")) {
            ps.setInt(1, TEST_CITY_ID);
            ps.executeUpdate();
        }
    }

    @Test
    public void testWarmingTrendAndPersist() throws Exception {
        String json = weatherForecaster.calculateAndSaveForecast(TEST_CITY_ID);
        assertNotNull(json);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        // Проверяем содержимое JSON
        assertEquals("Потепление", root.get("trend").asText());
        assertTrue(root.has("forecast"));
        assertEquals(3, root.get("forecast").size());

        // Проверяем, что прогноз записался в БД
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT result_data FROM analytics_results WHERE city_id = ? AND record_type = 'FORECAST'")) {
            ps.setInt(1, TEST_CITY_ID);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Запись с прогнозом должна быть в базе данных");
                String dbJson = rs.getString("result_data");
                assertNotNull(dbJson);

                JsonNode dbRoot = mapper.readTree(dbJson);
                assertEquals("Потепление", dbRoot.get("trend").asText());
                assertTrue(dbRoot.has("forecast"));
                assertEquals(3, dbRoot.get("forecast").size());
            }
        }
    }
}
