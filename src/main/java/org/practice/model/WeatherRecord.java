package org.practice.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WeatherRecord {
    private Integer cityId;
    private LocalDateTime timestamp;
    private Double temperature;
    private Integer humidity;
    private Double pressure;
    private Double windSpeed;
    private Double precipitation;
    private Integer cloudCover;
}