package com.davyie.openweather_adapter.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record City(int id, String name, String country, long timezone, long sunrise, long sunset) {}
