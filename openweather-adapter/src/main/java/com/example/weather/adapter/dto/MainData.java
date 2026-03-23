package com.example.weather.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MainData(
    double temp,
    @JsonProperty("temp_min") double tempMin,
    @JsonProperty("temp_max") double tempMax) {}
