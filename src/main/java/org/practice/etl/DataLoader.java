package org.practice.etl;

import org.practice.config.DatabaseConfig;
import org.practice.model.WeatherRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class DataLoader {
    private static final Logger logger = LoggerFactory.getLogger(DataLoader.class);

    private static final int BATCH_SIZE = 1000;

    public void loadWeatherData(List<WeatherRecord> records) {
        if (records == null || records.isEmpty()) {
            logger.warn("Список записей для загрузки пуст.");
            return;
        }

        String sql = """
                INSERT INTO weather_records \
                (city_id, timestamp, temperature, humidity, pressure, wind_speed, precipitation, cloud_cover) \
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) \
                ON CONFLICT (city_id, timestamp) DO NOTHING"""; // защита от дубликатов

        logger.info("Начало сохранения {} записей в базу данных...", records.size());

        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                int count = 0;
                int insertedTotal = 0;

                for (WeatherRecord record : records) {
                    preparedStatement.setInt(1, record.getCityId());
                    preparedStatement.setTimestamp(2, Timestamp.valueOf(record.getTimestamp()));
                    preparedStatement.setObject(3, record.getTemperature());
                    preparedStatement.setObject(4, record.getHumidity());
                    preparedStatement.setObject(5, record.getPressure());
                    preparedStatement.setObject(6, record.getWindSpeed());
                    preparedStatement.setObject(7, record.getPrecipitation());
                    preparedStatement.setObject(8, record.getCloudCover());

                    preparedStatement.addBatch();
                    count++;
                    if (count % BATCH_SIZE == 0) {
                        int[] result = preparedStatement.executeBatch();
                        insertedTotal += countBatchRows(result);
                        logger.info("Пакет отправлен. Успешно обработано строк в пакете: {}", count);
                        count = 0;
                    }
                }

                if (count > 0) {
                    int[] result = preparedStatement.executeBatch();
                    insertedTotal += countBatchRows(result);
                }

                connection.commit();
                logger.info("Загрузка данных успешно завершена! Всего сохранено строк: {}", insertedTotal);
            } catch (SQLException e) {
                logger.error("Ошибка при выполнении пакета запросов. Запускается откат транзакции...", e);
                connection.rollback();
                logger.info("Откат транзакции успешно выполнен. База данных возвращена в исходное состояние.");
            }
        } catch (SQLException e) {
            logger.error("Критическая ошибка SQL при загрузке данных в БД: {}", e.getMessage(), e);
        }
    }

    private int countBatchRows(int[] batchResult) {
        int total = 0;
        for (int res : batchResult) {
            if (res >= 0 || res == PreparedStatement.SUCCESS_NO_INFO) {
                total++;
            }
        }
        return total;
    }
}