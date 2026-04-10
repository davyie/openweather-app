package com.davyie.weather_web_api.controllers;

import com.davyie.TemperatureUnit;
import com.davyie.WeatherProvider;
import com.davyie.models.DayTemperature;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/weather")
public class WeatherController {

  private final WeatherProvider weatherProvider;

  public WeatherController(WeatherProvider weatherProvider) {
    this.weatherProvider = weatherProvider;
  }

  @GetMapping("/hello")
  public Mono<String> helloWorld() {
    return Mono.just("Hello World");
  }

  @GetMapping("/summary")
  public Flux<DayTemperature> getSummary(
      @RequestParam TemperatureUnit unit,
      @RequestParam Integer temperature,
      @RequestParam List<Integer> locations) {
    return weatherProvider.getOneDayWeatherForLocations(unit, temperature, locations);
  }

  @GetMapping("/locations/{locationId}")
  public Mono<List<DayTemperature>> getLocation(@PathVariable Integer locationId) {

    return weatherProvider.getFiveDaysWeatherForLocation(locationId);
  }
}
