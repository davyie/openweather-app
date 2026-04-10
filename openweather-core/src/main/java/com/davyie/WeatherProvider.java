package com.davyie;

import com.davyie.models.DayTemperature;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface WeatherProvider {
  Mono<List<DayTemperature>> getFiveDaysWeatherForLocation(Integer locationId);

  Flux<DayTemperature> getOneDayWeatherForLocations(
      TemperatureUnit unit, Integer temperature, List<Integer> locations);
}
