package com.davyie.openweather_adapter.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// The individual data points in the "list" array
@JsonIgnoreProperties(ignoreUnknown = true)
public record ForecastItem(
    long dt,
    MainData main,
    List<Weather> weather,
    Clouds clouds,
    Wind wind,
    Integer visibility,
    double pop,
    @JsonProperty("dt_txt") String dtTxt) {}
