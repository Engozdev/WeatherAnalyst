package org.practice.analytics;

import org.practice.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AnomalyDetector {
    private static final Logger logger = LoggerFactory.getLogger(AnomalyDetector.class);

    // Выявление периодов с аномально высокой или низкой температурой
    // с использованием исторического среднего и стандартного отклонения
    public List<String> findTemperatureAnomalies(int cityId, int targetYear, int targetMonth) {
        List<String> anomalies = new ArrayList<>();

        // Считаем историческую норму и стандартное отклонение за указанный месяц по
        // всем годам.
        // Ищем записи в целевом году, где температура выходит за пределы (Норма +-
        // 2*Отклонения).
        String sql = """
                WITH historical_stats AS (\
                   SELECT EXTRACT(MONTH FROM timestamp) as month, \
                          AVG(temperature) as hist_avg, \
                          STDDEV(temperature) as hist_stddev \
                   FROM weather_records \
                   WHERE city_id = ? AND EXTRACT(MONTH FROM timestamp) = ? \
                   GROUP BY month\
                ) \
                SELECT w.timestamp, w.temperature, s.hist_avg, s.hist_stddev \
                FROM weather_records w \
                JOIN historical_stats s ON EXTRACT(MONTH FROM w.timestamp) = s.month \
                WHERE w.city_id = ? \
                  AND EXTRACT(YEAR FROM w.timestamp) = ? \
                  AND EXTRACT(MONTH FROM w.timestamp) = ? \
                  AND (w.temperature > (s.hist_avg + 2 * s.hist_stddev) \
                       OR w.temperature < (s.hist_avg - 2 * s.hist_stddev)) \
                ORDER BY w.timestamp""";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, cityId);
            ps.setInt(2, targetMonth);
            ps.setInt(3, cityId);
            ps.setInt(4, targetYear);
            ps.setInt(5, targetMonth);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String timestamp = rs.getString("timestamp");
                double temp = rs.getDouble("temperature");
                double avg = rs.getDouble("hist_avg");

                String type = temp > avg ? "Аномальная ЖАРА" : "Аномальный ХОЛОД";
                String record = String.format(java.util.Locale.US, "%s: %s | Факт: %.1f°C | Норма: %.1f°C",
                        timestamp, type, temp, avg);
                anomalies.add(record);
            }
        } catch (SQLException e) {
            logger.error("Ошибка при поиске аномалий температуры", e);
        }
        return anomalies;
    }

    // Выявление резких перепадов температуры за 24 часа
    public List<String> findSuddenTemperatureDrops(int cityId, int year, double tempDropThreshold) {
        List<String> anomalies = new ArrayList<>();
        String sql = """
                SELECT w1.timestamp, w1.temperature, w2.temperature as prev_temp, (w2.temperature - w1.temperature) as drop_amount \
                FROM weather_records w1 \
                JOIN weather_records w2 ON w2.city_id = w1.city_id AND w2.timestamp = w1.timestamp - INTERVAL '24 hours' \
                WHERE w1.city_id = ? \
                  AND EXTRACT(YEAR FROM w1.timestamp) = ? \
                  AND (w2.temperature - w1.temperature) >= ? \
                ORDER BY w1.timestamp""";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, year);
            ps.setDouble(3, tempDropThreshold);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String timestamp = rs.getString("timestamp");
                double temp = rs.getDouble("temperature");
                double prevTemp = rs.getDouble("prev_temp");
                double drop = rs.getDouble("drop_amount");
                String record = String.format(java.util.Locale.US,
                        "%s: Резкое падение температуры | Факт: %.1f°C | Было: %.1f°C | Падение: %.1f°C",
                        timestamp, temp, prevTemp, drop);
                anomalies.add(record);
            }
        } catch (SQLException e) {
            logger.error("Ошибка при поиске резких падений температуры", e);
        }
        return anomalies;
    }

    // Выявление необычно высокого уровня осадков
    public List<String> findAnomalousPrecipitation(int cityId, int year) {
        List<String> anomalies = new ArrayList<>();
        String sql = """
                WITH daily_precip AS ( \
                    SELECT DATE(timestamp) as obs_date, \
                           EXTRACT(MONTH FROM timestamp) as month, \
                           SUM(precipitation) as total_precip \
                    FROM weather_records \
                    WHERE city_id = ? \
                    GROUP BY obs_date, month \
                ), \
                precip_stats AS ( \
                    SELECT month, \
                           AVG(total_precip) as avg_precip, \
                           STDDEV(total_precip) as stddev_precip \
                    FROM daily_precip \
                    GROUP BY month \
                ) \
                SELECT dp.obs_date, dp.total_precip, ps.avg_precip, ps.stddev_precip \
                FROM daily_precip dp \
                JOIN precip_stats ps ON dp.month = ps.month \
                WHERE dp.total_precip > (ps.avg_precip + 2 * COALESCE(ps.stddev_precip, 0)) \
                  AND EXTRACT(YEAR FROM dp.obs_date) = ? \
                  AND dp.total_precip > 0 \
                ORDER BY dp.obs_date""";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, year);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String date = rs.getDate("obs_date").toString();
                double precip = rs.getDouble("total_precip");
                double avg = rs.getDouble("avg_precip");
                String record = String.format(java.util.Locale.US,
                        "%s: Аномальные осадки | Факт: %.1f мм | Норма: %.1f мм",
                        date, precip, avg);
                anomalies.add(record);
            }
        } catch (SQLException e) {
            logger.error("Ошибка при поиске аномальных осадков", e);
        }
        return anomalies;
    }

    // Выявление аномально сильного ветра
    public List<String> findAnomalousWindSpeed(int cityId, int year) {
        List<String> anomalies = new ArrayList<>();
        String sql = """
                WITH wind_stats AS ( \
                    SELECT AVG(wind_speed) as avg_wind, \
                           STDDEV(wind_speed) as stddev_wind \
                    FROM weather_records \
                    WHERE city_id = ? \
                ) \
                SELECT w.timestamp, w.wind_speed, s.avg_wind, s.stddev_wind \
                FROM weather_records w \
                CROSS JOIN wind_stats s \
                WHERE w.city_id = ? \
                  AND EXTRACT(YEAR FROM w.timestamp) = ? \
                  AND w.wind_speed > (s.avg_wind + 2 * COALESCE(s.stddev_wind, 0)) \
                ORDER BY w.timestamp""";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, cityId);
            ps.setInt(3, year);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String timestamp = rs.getString("timestamp");
                double speed = rs.getDouble("wind_speed");
                double avg = rs.getDouble("avg_wind");
                String record = String.format(java.util.Locale.US,
                        "%s: Аномальный ветер | Скорость: %.1f м/с | Средняя: %.1f м/с",
                        timestamp, speed, avg);
                anomalies.add(record);
            }
        } catch (SQLException e) {
            logger.error("Ошибка при поиске аномального ветра", e);
        }
        return anomalies;
    }

    // Выявление длительных периодов без осадков
    public List<String> findDrySpells(int cityId, int year, int consecutiveDaysThreshold) {
        List<String> anomalies = new ArrayList<>();
        String sql = """
                SELECT DATE(timestamp) as obs_date, SUM(precipitation) as daily_precip \
                FROM weather_records \
                WHERE city_id = ? AND EXTRACT(YEAR FROM timestamp) = ? \
                GROUP BY obs_date \
                ORDER BY obs_date""";

        class DailyRecord {
            java.time.LocalDate date;
            double precip;

            DailyRecord(java.time.LocalDate date, double precip) {
                this.date = date;
                this.precip = precip;
            }
        }

        List<DailyRecord> records = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, year);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                records.add(new DailyRecord(rs.getDate("obs_date").toLocalDate(), rs.getDouble("daily_precip")));
            }
        } catch (SQLException e) {
            logger.error("Ошибка при получении осадков для поиска сухих периодов", e);
            return anomalies;
        }

        List<DailyRecord> currentSpell = new ArrayList<>();
        for (DailyRecord r : records) {
            if (r.precip == 0.0) {
                if (currentSpell.isEmpty()) {
                    currentSpell.add(r);
                } else {
                    java.time.LocalDate lastDate = currentSpell.get(currentSpell.size() - 1).date;
                    if (r.date.equals(lastDate.plusDays(1))) {
                        currentSpell.add(r);
                    } else {
                        if (currentSpell.size() >= consecutiveDaysThreshold) {
                            String start = currentSpell.get(0).date.toString();
                            String end = lastDate.toString();
                            anomalies.add(String.format("Период без осадков: с %s по %s (длительность: %d дней)",
                                    start, end, currentSpell.size()));
                        }
                        currentSpell.clear();
                        currentSpell.add(r);
                    }
                }
            } else {
                if (currentSpell.size() >= consecutiveDaysThreshold) {
                    String start = currentSpell.get(0).date.toString();
                    String end = currentSpell.get(currentSpell.size() - 1).date.toString();
                    anomalies.add(String.format("Период без осадков: с %s по %s (длительность: %d дней)",
                            start, end, currentSpell.size()));
                }
                currentSpell.clear();
            }
        }
        if (currentSpell.size() >= consecutiveDaysThreshold) {
            String start = currentSpell.get(0).date.toString();
            String end = currentSpell.get(currentSpell.size() - 1).date.toString();
            anomalies.add(String.format("Период без осадков: с %s по %s (длительность: %d дней)",
                    start, end, currentSpell.size()));
        }

        return anomalies;
    }
}