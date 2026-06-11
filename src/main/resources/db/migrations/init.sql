CREATE TABLE cities
(
    id        SERIAL PRIMARY KEY,
    name      VARCHAR(100)  NOT NULL UNIQUE,
    latitude  NUMERIC(6, 4) NOT NULL,
    longitude NUMERIC(7, 4) NOT NULL
);

CREATE TABLE weather_records
(
    id            BIGSERIAL PRIMARY KEY,
    city_id       INTEGER REFERENCES cities (id) ON DELETE CASCADE,
    timestamp     TIMESTAMP NOT NULL,
    temperature   NUMERIC(4, 1),
    humidity      NUMERIC(3, 0),
    pressure      NUMERIC(6, 1),
    wind_speed    NUMERIC(4, 1),
    precipitation NUMERIC(5, 2),
    cloud_cover   NUMERIC(3, 0),
    UNIQUE (city_id, timestamp)
);

CREATE TABLE analytics_results
(
    id               SERIAL PRIMARY KEY,
    city_id          INTEGER REFERENCES cities (id) ON DELETE CASCADE,
    calculation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    record_type      VARCHAR(50) NOT NULL, -- 'ANOMALY' или 'FORECAST'
    metric_name      VARCHAR(50),
    result_data      JSONB       NOT NULL  -- поле для хранения массивов прогноза или описания аномалии
);

CREATE INDEX idx_weather_city_timestamp ON weather_records(city_id, timestamp);