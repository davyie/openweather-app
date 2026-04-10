package com.davyie.openweather_adapter.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MainData(
    double temp,
    @JsonProperty("feels_like") double feelsLike,
    @JsonProperty("temp_min") double tempMin,
    @JsonProperty("temp_max") double tempMax,
    int pressure,
    @JsonProperty("sea_level") int seaLevel,
    @JsonProperty("grnd_level") int grndLevel,
    int humidity) {}
