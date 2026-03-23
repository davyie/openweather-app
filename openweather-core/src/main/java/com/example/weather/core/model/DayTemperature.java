package com.example.weather.core.model;

import java.time.LocalDate;

public record DayTemperature(LocalDate date, Temperature min, Temperature max) {

  public DayTemperature toUnit(TemperatureUnit targetUnit) {
    return new DayTemperature(date, min.toUnit(targetUnit), max.toUnit(targetUnit));
  }
}
