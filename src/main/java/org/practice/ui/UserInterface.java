package org.practice.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.practice.config.DatabaseConfig;
import org.practice.etl.WeatherEtlManager;
import org.practice.forecasting.WeatherForecaster;
import org.practice.model.City;
import org.practice.visualization.ReportGenerator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

public class UserInterface {
    private static final WeatherForecaster weatherForecaster = new WeatherForecaster();
    private static final ReportGenerator reportGenerator = new ReportGenerator();
    private static final WeatherEtlManager etlManager = new WeatherEtlManager();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void startInteractiveLoop() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Weather Analyst CLI");

        while (true) {
            City city = selectOrCreateCityMenu(scanner);
            if (city == null) {
                System.out.println("Завершение работы CLI.");
                break;
            }

            int year = promptForYear(scanner);
            boolean stayWithCity = true;

            while (stayWithCity) {
                printMainMenu(city, year);
                int choice = readInt(scanner, "Выберите действие (1-4): ");
                switch (choice) {
                    case 1 -> showWeatherForecast(city);
                    case 2 -> generatePdfReport(city, year);
                    case 3 -> stayWithCity = false;
                    case 4 -> {
                        return;
                    }
                    default -> System.out.println("Некорректный выбор. Пожалуйста, введите число от 1 до 4.");
                }
            }
        }
    }

    private static City selectOrCreateCityMenu(Scanner scanner) {
        while (true) {
            System.out.println("\n--- Выбор города ---");
            System.out.println("1. Выбрать существующий город");
            System.out.println("2. Добавить новый город");
            System.out.println("3. Выйти");

            int choice = readInt(scanner, "Выберите вариант (1-3): ");
            if (choice == 3) {
                return null;
            }

            if (choice == 1) {
                List<City> cities = getAllCities();
                if (cities.isEmpty()) {
                    System.out.println("В базе данных пока нет городов. Добавьте новый город.");
                    continue;
                }
                System.out.println("\nСписок доступных городов:");
                for (int i = 0; i < cities.size(); i++) {
                    City c = cities.get(i);
                    System.out.printf("%d. %s (Широта: %.4f, Долгота: %.4f)\n", i + 1, c.getName(), c.getLatitude(),
                            c.getLongitude());
                }
                System.out.printf("%d. Назад\n", cities.size() + 1);
                int cityIndex = readInt(scanner, "Выберите город по номеру (1-" + cities.size() + "): ");
                if (cityIndex >= 1 && cityIndex <= cities.size()) {
                    return cities.get(cityIndex - 1);
                } else if (cityIndex == cities.size() + 1) {
                    continue;
                } else {
                    System.out.println("Неверный номер города.");
                }
            } else if (choice == 2) {
                System.out.print("Введите название города: ");
                String name = scanner.nextLine().trim();
                if (name.isEmpty()) {
                    System.out.println("Название города не может быть пустым.");
                    continue;
                }

                City existing = getCityByName(name);
                if (existing != null) {
                    System.out.println("Город с таким названием уже существует: " + existing.getName());
                    System.out.print("Использовать его? (д/н): ");
                    String answer = scanner.nextLine().trim().toLowerCase();
                    if (answer.startsWith("д") || answer.startsWith("y")) {
                        return existing;
                    }
                    continue;
                }

                double latitude = readDouble(scanner, "Введите широту (latitude): ");
                double longitude = readDouble(scanner, "Введите долготу (longitude): ");

                City newCity = addCity(name, latitude, longitude);
                if (newCity != null) {
                    System.out
                            .println("Город успешно добавлен: " + newCity.getName() + " (ID: " + newCity.getId() + ")");

                    System.out.println("Запуск импорта исторических данных (ETL) за последние 5 лет...");
                    java.time.LocalDate today = java.time.LocalDate.now();
                    java.time.LocalDate fiveYearsAgo = today.minusYears(5);
                    try {
                        etlManager.runImportPipeline(newCity, fiveYearsAgo.toString(), today.toString());
                    } catch (Exception e) {
                        System.out.println("Ошибка при выполнении импорта: " + e.getMessage());
                    }
                    return newCity;
                } else {
                    System.out.println("Не удалось добавить город.");
                }
            } else {
                System.out.println("Некорректный выбор.");
            }
        }
    }

    private static int promptForYear(Scanner scanner) {
        while (true) {
            int year = readInt(scanner, "\nВведите год для анализа (2021 - 2025): ");
            if (year > 1900 && year < 2100) {
                return year;
            }
            System.out.println("Некорректный год. Пожалуйста, укажите год в диапазоне 2021 - 2025.");
        }
    }

    private static void printMainMenu(City city, int year) {
        System.out.println("\n--- Выбран город: " + city.getName() + " | Год анализа: " + year + " ---");
        System.out.println("1. Вывести прогноз погоды на 3 дня");
        System.out.println("2. Сформировать и сохранить итоговый PDF-отчет");
        System.out.println("3. Сменить город/период");
        System.out.println("4. Выйти");
    }

    private static void showWeatherForecast(City city) {
        System.out.println("\n=== Прогноз погоды на 3 дня для города " + city.getName() + " ===");
        try {
            String forecastJson = weatherForecaster.calculateAndSaveForecast(city.getId());
            JsonNode root = objectMapper.readTree(forecastJson);
            String trend = root.get("trend").asText();
            System.out.println("Ожидаемый температурный тренд: " + trend);

            JsonNode forecastArray = root.get("forecast");
            for (JsonNode dayNode : forecastArray) {
                int day = dayNode.get("day").asInt();
                String date = dayNode.get("date").asText();
                double avg = dayNode.get("avg_temp").asDouble();
                double min = dayNode.get("min_temp").asDouble();
                double max = dayNode.get("max_temp").asDouble();
                System.out.printf("День %d (%s): Ср. темп: %.1f°C [Диапазон: %.1f°C ... %.1f°C]\n",
                        day, date, avg, min, max);
            }
        } catch (Exception e) {
            System.out.println(
                    "Прогноз недоступен: недостаточно исторических данных для построения регрессионной модели.");
        }
    }

    private static void generatePdfReport(City city, int year) {
        String pdfName = "weather_report_" + city.getName() + "_" + year + ".pdf";
        System.out.println("\nГенерация PDF-отчета...");
        try {
            reportGenerator.generatePdfReport(city.getId(), year, pdfName);
            System.out.println("Отчет успешно сохранен в корневой директории проекта как: " + pdfName);
        } catch (Exception e) {
            System.out.println("Ошибка при генерации PDF-отчета: " + e.getMessage());
        }
    }

    // Вспомогательные методы работы с БД
    private static List<City> getAllCities() {
        List<City> cities = new ArrayList<>();
        String sql = "SELECT id, name, latitude, longitude FROM cities ORDER BY id";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cities.add(new City(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude")));
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при получении списка городов: " + e.getMessage());
        }
        return cities;
    }

    private static City getCityByName(String name) {
        String sql = "SELECT id, name, latitude, longitude FROM cities WHERE LOWER(name) = LOWER(?)";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new City(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при поиске города: " + e.getMessage());
        }
        return null;
    }

    private static City addCity(String name, double latitude, double longitude) {
        String sql = "INSERT INTO cities (name, latitude, longitude) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setDouble(2, latitude);
            ps.setDouble(3, longitude);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return new City(id, name, latitude, longitude);
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при добавлении города в БД: " + e.getMessage());
        }
        return null;
    }

    // Утилиты чтения ввода
    private static int readInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: Введите целое число.");
            }
        }
    }

    private static double readDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Double.parseDouble(line);
            } catch (NumberFormatException e) {
                System.out.println("Ошибка: Введите число.");
            }
        }
    }
}
