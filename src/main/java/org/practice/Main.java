package org.practice;

import org.practice.etl.WeatherEtlManager;
import org.practice.model.City;
import org.practice.visualization.ReportGenerator;

public class Main {
    public static void main(String[] args) {
        City spb = new City(2, "Кейптаун", 33.5500, 18.2900);
        WeatherEtlManager etlManager = new WeatherEtlManager();
        String startDate = "2021-06-10";
        String endDate = "2026-06-18";

        etlManager.runImportPipeline(spb, startDate, endDate);

        ReportGenerator reportGenerator = new ReportGenerator();
        reportGenerator.generatePdfReport(2, 2025, "weather_report_kpt_2025_new.pdf");
    }
}