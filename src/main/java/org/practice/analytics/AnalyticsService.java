package org.practice.analytics;

import org.practice.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class AnalyticsService {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    // Расчет средней, минимальной и максимальной температуры за выбранный год
    public Map<String, Double> getYearlyTemperatureStats(int cityId, int year) {
        String sql = """
                SELECT MIN(temperature) as min_temp, MAX(temperature) as max_temp, AVG(temperature) as avg_temp \
                FROM weather_records WHERE city_id = ? AND EXTRACT(YEAR FROM timestamp) = ?""";

        Map<String, Double> stats = new HashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, year);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                stats.put("MIN", rs.getDouble("min_temp"));
                stats.put("MAX", rs.getDouble("max_temp"));
                stats.put("AVG", rs.getDouble("avg_temp"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка при расчете статистики за год", e);
        }
        return stats;
    }

    // Анализ изменения температуры по месяцам за выбранный год
    public Map<Integer, Double> getMonthlyAverageTemperature(int cityId, int year) {
        String sql = """
                SELECT EXTRACT(MONTH FROM timestamp) as month, AVG(temperature) as avg_temp \
                FROM weather_records WHERE city_id = ? AND EXTRACT(YEAR FROM timestamp) = ? \
                GROUP BY month ORDER BY month""";

        Map<Integer, Double> monthlyStats = new LinkedHashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, year);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                monthlyStats.put(rs.getInt("month"), rs.getDouble("avg_temp"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка при расчете среднемесячной температуры", e);
        }
        return monthlyStats;
    }

    // Количество дней с осадками выше заданного порога
    public int getDaysWithHeavyPrecipitation(int cityId, int year, double thresholdMm) {
        // используем подзапрос, чтобы сначала просуммировать осадки за день, а потом
        // отфильтровать
        String sql = """
                SELECT COUNT(*) FROM (\
                  SELECT DATE(timestamp) as obs_date, SUM(precipitation) as daily_precip \
                  FROM weather_records WHERE city_id = ? AND EXTRACT(YEAR FROM timestamp) = ? \
                  GROUP BY obs_date\
                ) as daily_data WHERE daily_precip > ?""";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, year);
            ps.setDouble(3, thresholdMm);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("Ошибка при расчете дней с осадками", e);
        }
        return 0;
    }

    // Анализ долгосрочных климатических тенденций (средняя температура по годам)
    public Map<Integer, Double> getLongTermTemperatureTrend(int cityId) {
        String sql = """
                SELECT EXTRACT(YEAR FROM timestamp) as year, AVG(temperature) as avg_temp \
                FROM weather_records WHERE city_id = ? \
                GROUP BY year ORDER BY year""";

        Map<Integer, Double> trend = new LinkedHashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                trend.put(rs.getInt("year"), rs.getDouble("avg_temp"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка расчета климатических трендов", e);
        }
        return trend;
    }

    // Расчет сезонной средней температуры за год
    public Map<String, Double> getSeasonalAverageTemperature(int cityId, int year) {
        String sql = """
                SELECT \
                  CASE \
                    WHEN EXTRACT(MONTH FROM timestamp) IN (12, 1, 2) THEN 'WINTER' \
                    WHEN EXTRACT(MONTH FROM timestamp) IN (3, 4, 5) THEN 'SPRING' \
                    WHEN EXTRACT(MONTH FROM timestamp) IN (6, 7, 8) THEN 'SUMMER' \
                    WHEN EXTRACT(MONTH FROM timestamp) IN (9, 10, 11) THEN 'AUTUMN' \
                  END as season, \
                  AVG(temperature) as avg_temp \
                FROM weather_records \
                WHERE city_id = ? AND EXTRACT(YEAR FROM timestamp) = ? \
                GROUP BY season""";

        Map<String, Double> stats = new HashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, year);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String season = rs.getString("season");
                if (season != null) {
                    stats.put(season, rs.getDouble("avg_temp"));
                }
            }
        } catch (SQLException e) {
            logger.error("Ошибка при расчете сезонной температуры", e);
        }
        return stats;
    }

    // Расчет общей статистики температуры
    public Map<String, Double> getAllTimeTemperatureStats(int cityId) {
        String sql = """
                SELECT MIN(temperature) as min_temp, MAX(temperature) as max_temp, AVG(temperature) as avg_temp \
                FROM weather_records WHERE city_id = ?""";

        Map<String, Double> stats = new HashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                stats.put("MIN", rs.getDouble("min_temp"));
                stats.put("MAX", rs.getDouble("max_temp"));
                stats.put("AVG", rs.getDouble("avg_temp"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка при расчете всеобщей статистики температуры", e);
        }
        return stats;
    }

    // Сравнение средних температур нескольких городов за указанный год
    public Map<String, Double> compareCitiesAverageTemperature(List<Integer> cityIds, int year) {
        Map<String, Double> results = new HashMap<>();
        if (cityIds == null || cityIds.isEmpty()) {
            return results;
        }

        String sql = """
                SELECT c.name, AVG(w.temperature) as avg_temp \
                FROM weather_records w \
                JOIN cities c ON w.city_id = c.id \
                WHERE w.city_id = ANY(?) AND EXTRACT(YEAR FROM w.timestamp) = ? \
                GROUP BY c.name""";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            java.sql.Array array = conn.createArrayOf("INTEGER", cityIds.toArray());
            ps.setArray(1, array);
            ps.setInt(2, year);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.put(rs.getString("name"), rs.getDouble("avg_temp"));
            }
        } catch (SQLException e) {
            logger.error("Ошибка при сравнении средних температур городов", e);
        }
        return results;
    }

    // Поиск экстремальных температурных периодов (дней с самыми высокими/низкими
    // средними температурами)
    public List<Map<String, Object>> getExtremeTemperaturePeriods(int cityId, int limit, boolean warmest) {
        String order = warmest ? "DESC" : "ASC";
        String sql = String.format("""
                SELECT DATE(timestamp) as obs_date, AVG(temperature) as daily_avg \
                FROM weather_records \
                WHERE city_id = ? \
                GROUP BY obs_date \
                ORDER BY daily_avg %s \
                LIMIT ?""", order);

        List<Map<String, Object>> periods = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("date", rs.getDate("obs_date").toString());
                map.put("temperature", rs.getDouble("daily_avg"));
                periods.add(map);
            }
        } catch (SQLException e) {
            logger.error("Ошибка при поиске экстремальных периодов температуры", e);
        }
        return periods;
    }
}