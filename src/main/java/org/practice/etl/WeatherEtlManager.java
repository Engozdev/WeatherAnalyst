package org.practice.etl;

import org.practice.model.City;
import org.practice.model.WeatherRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class WeatherEtlManager {
    private static final Logger logger = LoggerFactory.getLogger(WeatherEtlManager.class);

    private final DataExtractor extractor;
    private final DataTransformer transformer;
    private final DataLoader loader;

    public WeatherEtlManager() {
        this.extractor = new DataExtractor();
        this.transformer = new DataTransformer();
        this.loader = new DataLoader();
    }

    // Запускает загрузку, очистку и сохранение данных в БД
    public void runImportPipeline(City city, String startDate, String endDate) {
        logger.info("ЗАПУСК ETL-ПРОЦЕССА ДЛЯ ГОРОДА: {} (Период: {} - {})",
                city.getName().toUpperCase(), startDate, endDate);

        long startTime = System.currentTimeMillis();

        // Подключение к источнику и загрузка сырых данных
        String rawJson = extractor.extractWeatherData(city, startDate, endDate);
        if (rawJson == null || rawJson.isBlank()) {
            logger.error("ETL Прерван: Не удалось извлечь данные из Open-Meteo API.");
            return;
        }

        // Парсинг, валидация и восстановление пропущенных значений
        List<WeatherRecord> cleanRecords = transformer.transform(rawJson, city.getId());
        if (cleanRecords.isEmpty()) {
            logger.warn("ETL Прерван: После этапа трансформации не найдено валидных записей.");
            return;
        }

        // Батчевое сохранение очищенных данных в postgresql
        loader.loadWeatherData(cleanRecords);

        long duration = System.currentTimeMillis() - startTime;
        logger.info(" ETL-ПРОЦЕСС ДЛЯ ГОРОДА {} УСПЕШНО ЗАВЕРШЕН ", city.getName().toUpperCase());
        logger.info("Успешно импортировано часов наблюдений: {}. Время выполнения: {} мс.",
                cleanRecords.size(), duration);
    }
}