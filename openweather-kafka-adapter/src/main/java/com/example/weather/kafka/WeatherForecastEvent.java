package com.example.weather.kafka;

import com.example.weather.core.model.DayTemperature;
import java.util.List;

public record WeatherForecastEvent(long locationId, List<DayTemperature> forecast) {}
