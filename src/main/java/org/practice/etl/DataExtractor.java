package org.practice.etl;

import org.practice.model.City;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public class DataExtractor {
    private static final Logger logger = LoggerFactory.getLogger(DataExtractor.class);
    private final HttpClient httpClient;

    public DataExtractor() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Загружает исторические погодные данные в формате JSON из Open-Meteo API
     */
    public String extractWeatherData(City city, String startDate, String endDate) {
        String url = String.format(Locale.ROOT,
                """
                        https://archive-api.open-meteo.com/v1/archive?\
                        latitude=%.4f&longitude=%.4f&start_date=%s&end_date=%s\
                        &hourly=temperature_2m,relative_humidity_2m,pressure_msl,wind_speed_10m,precipitation,cloud_cover""",
                city.getLatitude(), city.getLongitude(), startDate, endDate
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            logger.info("Отправка запроса к Open-Meteo API для города: {}", city.getName());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                logger.error("Ошибка загрузки данных. Сервер вернул код: {}. Ответ: {}", response.statusCode(), response.body());
                return null;
            }

        } catch (Exception e) {
            logger.error("Критическая ошибка при подключении к API Open-Meteo: {}", e.getMessage(), e);
            return null;
        }
    }
}