package org.practice;

import org.practice.etl.WeatherEtlManager;
import org.practice.model.City;

public class Main {
    public static void main(String[] args) {
        City spb = new City(1, "Санкт-Петербург", 59.9386, 30.3141);
        WeatherEtlManager etlManager = new WeatherEtlManager();
        String startDate = "2021-06-10";
        String endDate = "2026-06-10";

        etlManager.runImportPipeline(spb, startDate, endDate);
    }
}