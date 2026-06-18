package org.practice.visualization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.practice.config.DatabaseConfig;
import org.practice.analytics.AnalyticsService;
import org.practice.analytics.AnomalyDetector;
import org.practice.forecasting.WeatherForecaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class ReportGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ReportGenerator.class);
    private final AnalyticsService analyticsService;
    private final AnomalyDetector anomalyDetector;
    private final WeatherForecaster weatherForecaster;
    private final ObjectMapper objectMapper;

    public ReportGenerator() {
        this.analyticsService = new AnalyticsService();
        this.anomalyDetector = new AnomalyDetector();
        this.weatherForecaster = new WeatherForecaster();
        this.objectMapper = new ObjectMapper();
    }

    public void generatePdfReport(int cityId, int year, String outputPath) {
        logger.info("Начало генерации PDF-отчета для города ID: {}, год: {}", cityId, year);
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            // Подключаем шрифт Arial для поддержки кириллицы
            BaseFont bf = BaseFont.createFont("C:\\Windows\\Fonts\\arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font fontTitle = new Font(bf, 18, Font.BOLD, Color.DARK_GRAY);
            Font fontSubtitle = new Font(bf, 12, Font.ITALIC, Color.GRAY);
            Font fontHeader = new Font(bf, 11, Font.BOLD, Color.BLACK);
            Font fontBody = new Font(bf, 9, Font.NORMAL, Color.BLACK);
            Font fontBodyBold = new Font(bf, 9, Font.BOLD, Color.BLACK);
            Font fontAnomaly = new Font(bf, 9, Font.NORMAL, new Color(180, 50, 50));

            PdfWriter.getInstance(document, new FileOutputStream(outputPath));
            document.open();

            CityInfo city = fetchCityInfo(cityId);
            if (city == null) {
                throw new IllegalArgumentException("Город с ID " + cityId + " не найден в справочнике.");
            }

            Paragraph title = new Paragraph("Итоговый аналитический погодный отчет", fontTitle);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(5);
            document.add(title);

            Paragraph subtitle = new Paragraph(String.format("Город: %s | Координаты: %s, %s | Период: %d год",
                    city.name, city.latitude, city.longitude, year), fontSubtitle);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(15);
            document.add(subtitle);

            document.add(new Paragraph("1. Сводные погодные показатели за год", fontHeader));
            Paragraph spacing = new Paragraph(" ");
            spacing.setSpacingBefore(5);
            document.add(spacing);

            PdfPTable statsTable = new PdfPTable(2);
            statsTable.setWidthPercentage(100);
            statsTable.setSpacingAfter(15);

            Map<String, Double> tempStats = analyticsService.getYearlyTemperatureStats(cityId, year);
            Map<String, Object> extraStats = fetchExtraYearlyStats(cityId, year);
            int heavyRainDays = analyticsService.getDaysWithHeavyPrecipitation(cityId, year, 20.0);

            addTableCell(statsTable, "Минимальная температура", fontBodyBold);
            addTableCell(statsTable, String.format(java.util.Locale.US, "%.1f °C", tempStats.getOrDefault("MIN", 0.0)),
                    fontBody);
            addTableCell(statsTable, "Максимальная температура", fontBodyBold);
            addTableCell(statsTable, String.format(java.util.Locale.US, "%.1f °C", tempStats.getOrDefault("MAX", 0.0)),
                    fontBody);
            addTableCell(statsTable, "Средняя температура", fontBodyBold);
            addTableCell(statsTable, String.format(java.util.Locale.US, "%.1f °C", tempStats.getOrDefault("AVG", 0.0)),
                    fontBody);
            addTableCell(statsTable, "Общее количество осадков", fontBodyBold);
            addTableCell(statsTable, String.format(java.util.Locale.US, "%.1f мм",
                    (Double) extraStats.getOrDefault("total_precip", 0.0)), fontBody);
            addTableCell(statsTable, "Количество дней с сильными осадками (>20 мм)", fontBodyBold);
            addTableCell(statsTable, String.valueOf(heavyRainDays), fontBody);
            addTableCell(statsTable, "Средняя скорость ветра", fontBodyBold);
            addTableCell(statsTable,
                    String.format(java.util.Locale.US, "%.1f м/с", (Double) extraStats.getOrDefault("avg_wind", 0.0)),
                    fontBody);

            document.add(statsTable);

            document.add(new Paragraph("2. Агрегированные графики метеорологических показателей", fontHeader));
            document.add(spacing);

            List<MonthlyStats> monthlyData = fetchMonthlyData(cityId, year);

            // График изменения температуры (Макс/Мин/Средняя по месяцам)
            JFreeChart tempChart = createTemperatureChart(monthlyData);
            Image tempChartImg = chartToImage(tempChart, 500, 160);
            tempChartImg.setAlignment(Element.ALIGN_CENTER);
            tempChartImg.setSpacingAfter(10);
            document.add(tempChartImg);

            // График осадков (Сумма по месяцам)
            JFreeChart precipChart = createPrecipitationChart(monthlyData);
            Image precipChartImg = chartToImage(precipChart, 500, 160);
            precipChartImg.setAlignment(Element.ALIGN_CENTER);
            precipChartImg.setSpacingAfter(15);
            document.add(precipChartImg);

            document.newPage();

            document.add(new Paragraph("3. Сравнение средней температуры по городам", fontHeader));
            document.add(spacing);

            Map<String, Double> cityComparisons = fetchCityComparisons(year);
            JFreeChart compareChart = createCityComparisonChart(cityComparisons);
            Image compareChartImg = chartToImage(compareChart, 500, 150);
            compareChartImg.setAlignment(Element.ALIGN_CENTER);
            compareChartImg.setSpacingAfter(15);
            document.add(compareChartImg);

            document.add(new Paragraph("4. Выявленные климатические аномалии (сгруппировано посуточно)", fontHeader));
            document.add(spacing);

            List<String> anomalies = fetchAllAnomaliesGrouped(cityId, year);
            if (anomalies.isEmpty()) {
                document.add(new Paragraph("За выбранный период аномалий не обнаружено.", fontBody));
            } else {
                PdfPTable anomalyTable = new PdfPTable(1);
                anomalyTable.setWidthPercentage(100);
                anomalyTable.setSpacingAfter(15);
                for (String anomaly : anomalies) {
                    addTableCell(anomalyTable, anomaly, fontAnomaly);
                }
                document.add(anomalyTable);
            }

            document.add(new Paragraph("5. Математический прогноз погоды на 3 дня", fontHeader));
            document.add(spacing);

            try {
                String forecastJson = weatherForecaster.calculateAndSaveForecast(cityId);
                JsonNode forecastRoot = objectMapper.readTree(forecastJson);
                String trend = forecastRoot.get("trend").asText();
                JsonNode forecastArray = forecastRoot.get("forecast");

                Paragraph forecastTrend = new Paragraph("Ожидаемый температурный тренд: " + trend, fontBodyBold);
                forecastTrend.setSpacingAfter(5);
                document.add(forecastTrend);

                PdfPTable forecastTable = new PdfPTable(5);
                forecastTable.setWidthPercentage(100);
                forecastTable.setSpacingAfter(10);

                addTableCell(forecastTable, "День", fontBodyBold);
                addTableCell(forecastTable, "Дата", fontBodyBold);
                addTableCell(forecastTable, "Средняя темп. (°C)", fontBodyBold);
                addTableCell(forecastTable, "Мин. темп. (°C)", fontBodyBold);
                addTableCell(forecastTable, "Макс. темп. (°C)", fontBodyBold);

                for (JsonNode node : forecastArray) {
                    addTableCell(forecastTable, String.valueOf(node.get("day").asInt()), fontBody);
                    addTableCell(forecastTable, node.get("date").asText(), fontBody);
                    addTableCell(forecastTable,
                            String.format(java.util.Locale.US, "%.1f", node.get("avg_temp").asDouble()), fontBody);
                    addTableCell(forecastTable,
                            String.format(java.util.Locale.US, "%.1f", node.get("min_temp").asDouble()), fontBody);
                    addTableCell(forecastTable,
                            String.format(java.util.Locale.US, "%.1f", node.get("max_temp").asDouble()), fontBody);
                }
                document.add(forecastTable);

            } catch (Exception e) {
                logger.warn("Не удалось рассчитать прогноз для отчета (возможно, мало данных): {}", e.getMessage());
                document.add(new Paragraph(
                        "Прогноз недоступен: недостаточно исторических данных для построения регрессионной модели.",
                        fontBody));
            }

            // Подпись и дата генерации
            Paragraph footer = new Paragraph(String.format("Отчет сформирован автоматически: %s",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))), fontSubtitle);
            footer.setAlignment(Element.ALIGN_RIGHT);
            footer.setSpacingBefore(15);
            document.add(footer);

            document.close();
            logger.info("PDF-отчет успешно создан и сохранен в: {}", outputPath);

        } catch (Exception e) {
            logger.error("Критическая ошибка при генерации PDF-отчета", e);
            throw new RuntimeException("Не удалось сгенерировать PDF-отчет", e);
        }
    }

    private void addTableCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(5);
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private Image chartToImage(JFreeChart chart, int width, int height) throws Exception {
        BufferedImage img = chart.createBufferedImage(width, height);
        ByteArrayOutputStream bas = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bas);
        return Image.getInstance(bas.toByteArray());
    }

    private CityInfo fetchCityInfo(int cityId) throws SQLException {
        String sql = "SELECT name, latitude, longitude FROM cities WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CityInfo(
                            rs.getString("name"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude"));
                }
            }
        }
        return null;
    }

    private Map<String, Object> fetchExtraYearlyStats(int cityId, int year) throws SQLException {
        String sql = """
                SELECT SUM(precipitation) as total_precip, AVG(wind_speed) as avg_wind \
                FROM weather_records \
                WHERE city_id = ? AND EXTRACT(YEAR FROM timestamp) = ?""";

        Map<String, Object> map = new HashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, year);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    map.put("total_precip", rs.getDouble("total_precip"));
                    map.put("avg_wind", rs.getDouble("avg_wind"));
                }
            }
        }
        return map;
    }

    private List<MonthlyStats> fetchMonthlyData(int cityId, int year) throws SQLException {
        String sql = """
                SELECT EXTRACT(MONTH FROM timestamp) as month, \
                       MIN(temperature) as min_temp, \
                       MAX(temperature) as max_temp, \
                       AVG(temperature) as avg_temp, \
                       SUM(precipitation) as total_precip \
                FROM weather_records \
                WHERE city_id = ? AND EXTRACT(YEAR FROM timestamp) = ? \
                GROUP BY month \
                ORDER BY month""";

        List<MonthlyStats> data = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setInt(2, year);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.add(new MonthlyStats(
                            rs.getInt("month"),
                            rs.getDouble("min_temp"),
                            rs.getDouble("max_temp"),
                            rs.getDouble("avg_temp"),
                            rs.getDouble("total_precip")));
                }
            }
        }
        return data;
    }

    private Map<String, Double> fetchCityComparisons(int year) throws SQLException {
        String sql = """
                SELECT c.name, AVG(w.temperature) as avg_temp \
                FROM weather_records w \
                JOIN cities c ON w.city_id = c.id \
                WHERE EXTRACT(YEAR FROM w.timestamp) = ? \
                GROUP BY c.name""";

        Map<String, Double> comparisons = new HashMap<>();
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    comparisons.put(rs.getString("name"), rs.getDouble("avg_temp"));
                }
            }
        }
        return comparisons;
    }

    private List<String> fetchAllAnomalies(int cityId, int year) {
        List<String> list = new ArrayList<>();
        // Температурные аномалии по месяцам
        for (int m = 1; m <= 12; m++) {
            list.addAll(anomalyDetector.findTemperatureAnomalies(cityId, year, m));
        }
        // Резкие падения температуры (порог 10 градусов за сутки)
        list.addAll(anomalyDetector.findSuddenTemperatureDrops(cityId, year, 10.0));
        // Аномальные осадки
        list.addAll(anomalyDetector.findAnomalousPrecipitation(cityId, year));
        // Аномальный ветер
        list.addAll(anomalyDetector.findAnomalousWindSpeed(cityId, year));
        // Сухие периоды (порог 5 дней)
        list.addAll(anomalyDetector.findDrySpells(cityId, year, 5));

        return list;
    }

    private List<String> fetchAllAnomaliesGrouped(int cityId, int year) {
        List<String> rawAnomalies = fetchAllAnomalies(cityId, year);
        List<String> grouped = new ArrayList<>();

        // Карта для группировки: Дата -> (Тип аномалии -> Список записей)
        Map<String, Map<String, List<HourlyAnomaly>>> hourlyGroupings = new LinkedHashMap<>();
        List<String> dailyAnomalies = new ArrayList<>(); // для осадков и сухих периодов

        for (String raw : rawAnomalies) {
            if (raw.startsWith("Период без осадков:") || raw.contains("Аномальные осадки")) {
                dailyAnomalies.add(raw);
                continue;
            }

            // Ожидаем в начале строку с меткой времени: "YYYY-MM-DD HH:mm:ss"
            if (raw.length() >= 19 && raw.charAt(4) == '-' && raw.charAt(7) == '-' && raw.charAt(10) == ' ') {
                String date = raw.substring(0, 10);
                String rest = raw.substring(21); // отрезаем метку времени и двоеточие

                String type;
                double val = 0.0;
                if (rest.contains("Аномальная ЖАРА")) {
                    type = "Аномальная ЖАРА";
                    val = parseDoubleField(rest, "Факт:");
                } else if (rest.contains("Аномальный ХОЛОД")) {
                    type = "Аномальный ХОЛОД";
                    val = parseDoubleField(rest, "Факт:");
                } else if (rest.contains("Резкое падение температуры")) {
                    type = "Резкое падение температуры";
                    val = parseDoubleField(rest, "Падение:");
                } else if (rest.contains("Аномальный ветер")) {
                    type = "Аномальный ветер";
                    val = parseDoubleField(rest, "Скорость:");
                } else {
                    dailyAnomalies.add(raw);
                    continue;
                }

                hourlyGroupings.computeIfAbsent(date, k -> new LinkedHashMap<>())
                        .computeIfAbsent(type, k -> new ArrayList<>())
                        .add(new HourlyAnomaly(val));
            } else {
                dailyAnomalies.add(raw);
            }
        }

        // Форматируем сгруппированные аномалии
        for (Map.Entry<String, Map<String, List<HourlyAnomaly>>> dateEntry : hourlyGroupings.entrySet()) {
            String date = dateEntry.getKey();
            for (Map.Entry<String, List<HourlyAnomaly>> typeEntry : dateEntry.getValue().entrySet()) {
                String type = typeEntry.getKey();
                List<HourlyAnomaly> list = typeEntry.getValue();
                int hours = list.size();

                double peakVal;
                if (type.equals("Аномальный ХОЛОД")) {
                    peakVal = list.stream().mapToDouble(a -> a.value).min().orElse(0.0);
                } else {
                    peakVal = list.stream().mapToDouble(a -> a.value).max().orElse(0.0);
                }

                String formatted = "";
                if (type.contains("ЖАРА") || type.contains("ХОЛОД")) {
                    formatted = String.format(java.util.Locale.US, "%s: %s | Пик: %.1f°C (продолжительность: %d ч)",
                            date, type, peakVal, hours);
                } else if (type.contains("падение")) {
                    formatted = String.format(java.util.Locale.US,
                            "%s: Резкое падение температуры | Макс. перепад: %.1f°C (продолжительность: %d ч)", date,
                            peakVal, hours);
                } else if (type.contains("ветер")) {
                    formatted = String.format(java.util.Locale.US,
                            "%s: Аномально сильный ветер | Пик: %.1f м/с (продолжительность: %d ч)", date, peakVal,
                            hours);
                }
                grouped.add(formatted);
            }
        }

        grouped.addAll(dailyAnomalies);

        // Сортируем хронологически
        grouped.sort((a, b) -> {
            String dateA = extractDateForSort(a);
            String dateB = extractDateForSort(b);
            return dateA.compareTo(dateB);
        });

        return grouped;
    }

    private double parseDoubleField(String text, String prefix) {
        try {
            int idx = text.indexOf(prefix);
            if (idx != -1) {
                String sub = text.substring(idx + prefix.length()).trim();
                int spaceIdx = sub.indexOf(" ");
                int pipeIdx = sub.indexOf("|");
                int endIdx = sub.length();
                if (spaceIdx != -1)
                    endIdx = Math.min(endIdx, spaceIdx);
                if (pipeIdx != -1)
                    endIdx = Math.min(endIdx, pipeIdx);
                String valStr = sub.substring(0, endIdx).replaceAll("[^0-9.-]", "");
                return Double.parseDouble(valStr);
            }
        } catch (Exception e) {
        }
        return 0.0;
    }

    private String extractDateForSort(String text) {
        for (int i = 0; i <= text.length() - 10; i++) {
            if (Character.isDigit(text.charAt(i)) &&
                    Character.isDigit(text.charAt(i + 1)) &&
                    Character.isDigit(text.charAt(i + 2)) &&
                    Character.isDigit(text.charAt(i + 3)) &&
                    text.charAt(i + 4) == '-' &&
                    Character.isDigit(text.charAt(i + 5)) &&
                    Character.isDigit(text.charAt(i + 6)) &&
                    text.charAt(i + 7) == '-' &&
                    Character.isDigit(text.charAt(i + 8)) &&
                    Character.isDigit(text.charAt(i + 9))) {
                return text.substring(i, i + 10);
            }
        }
        return text;
    }

    private JFreeChart createTemperatureChart(List<MonthlyStats> monthlyData) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String[] monthNames = { "Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек" };
        for (MonthlyStats m : monthlyData) {
            String label = (m.month >= 1 && m.month <= 12) ? monthNames[m.month - 1] : String.valueOf(m.month);
            dataset.addValue(m.maxTemp, "Максимум", label);
            dataset.addValue(m.avgTemp, "Средняя", label);
            dataset.addValue(m.minTemp, "Минимум", label);
        }
        return ChartFactory.createLineChart(
                "Месячные температурные показатели за год",
                "Месяц",
                "Температура (°C)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false);
    }

    private JFreeChart createPrecipitationChart(List<MonthlyStats> monthlyData) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String[] monthNames = { "Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек" };
        for (MonthlyStats m : monthlyData) {
            String label = (m.month >= 1 && m.month <= 12) ? monthNames[m.month - 1] : String.valueOf(m.month);
            dataset.addValue(m.totalPrecip, "Сумма осадков", label);
        }
        return ChartFactory.createBarChart(
                "Сумма осадков по месяцам",
                "Месяц",
                "Осадки (мм)",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false);
    }

    private JFreeChart createCityComparisonChart(Map<String, Double> cityComparisons) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, Double> entry : cityComparisons.entrySet()) {
            dataset.addValue(entry.getValue(), "Средняя температура", entry.getKey());
        }
        return ChartFactory.createBarChart(
                "Сравнение средней температуры по городам",
                "Город",
                "Температура (°C)",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false);
    }

    private static class CityInfo {
        String name;
        double latitude;
        double longitude;

        CityInfo(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private static class MonthlyStats {
        int month;
        double minTemp;
        double maxTemp;
        double avgTemp;
        double totalPrecip;

        MonthlyStats(int month, double minTemp, double maxTemp, double avgTemp, double totalPrecip) {
            this.month = month;
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
            this.avgTemp = avgTemp;
            this.totalPrecip = totalPrecip;
        }
    }

    private static class HourlyAnomaly {
        double value;

        HourlyAnomaly(double value) {
            this.value = value;
        }
    }
}
