package com.example.weather.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ForecastItem(long dt, MainData main, @JsonProperty("dt_txt") String dtTxt) {}
