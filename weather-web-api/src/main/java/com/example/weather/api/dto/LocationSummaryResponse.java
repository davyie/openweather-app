package com.example.weather.api.dto;

import java.time.LocalDate;

public record LocationSummaryResponse(
    long locationId, LocalDate date, double minTemperature, double maxTemperature, String unit) {}
