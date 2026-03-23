package com.example.weather.starter;

import com.example.weather.core.WeatherProvider;
import com.example.weather.core.model.DayTemperature;
import com.example.weather.core.model.Temperature;
import com.example.weather.core.model.TemperatureUnit;
import java.time.LocalDate;
import reactor.core.publisher.Flux;

public class FakeWeatherProvider implements WeatherProvider {

  @Override
  public Flux<DayTemperature> getForecast(long locationId) {
    LocalDate today = LocalDate.now();
    return Flux.range(0, 5)
        .map(
            i ->
                new DayTemperature(
                    today.plusDays(i),
                    new Temperature(15.0 + i, TemperatureUnit.CELSIUS),
                    new Temperature(25.0 + i, TemperatureUnit.CELSIUS)));
  }
}
