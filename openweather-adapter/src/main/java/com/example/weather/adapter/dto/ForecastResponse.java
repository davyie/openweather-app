package com.example.weather.adapter.dto;

import java.util.List;

public record ForecastResponse(String cod, List<ForecastItem> list) {}
