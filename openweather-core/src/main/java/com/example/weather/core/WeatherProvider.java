package com.example.weather.core;

import com.example.weather.core.model.DayTemperature;
import reactor.core.publisher.Flux;

public interface WeatherProvider {

  Flux<DayTemperature> getForecast(long locationId);
}
