package com.davyie.openweather_adapter.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ForecastResponse(
    String cod, int message, int cnt, List<ForecastItem> list, City city) {}
