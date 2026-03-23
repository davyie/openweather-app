package com.example.weather.api.dto;

import java.time.LocalDate;

public record DayTemperatureResponse(
    LocalDate date, double minTemperature, double maxTemperature, String unit) {}
