package com.example.weather.api;

import com.example.weather.api.dto.DayTemperatureResponse;
import com.example.weather.api.dto.LocationSummaryResponse;
import com.example.weather.core.WeatherProvider;
import com.example.weather.core.model.DayTemperature;
import com.example.weather.core.model.TemperatureUnit;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
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
  public Mono<String> hello() {
    return Mono.just("OK");
  }

  @GetMapping("/summary")
  public Flux<LocationSummaryResponse> summary(
      @RequestParam String unit, @RequestParam double temperature, @RequestParam String locations) {

    TemperatureUnit temperatureUnit = parseUnit(unit);
    List<Long> locationIds = parseLocations(locations);
    LocalDate tomorrow = LocalDate.now().plusDays(1);

    return Flux.fromIterable(locationIds)
        .flatMap(
            locationId ->
                weatherProvider
                    .getForecast(locationId)
                    .filter(day -> day.date().equals(tomorrow))
                    .next()
                    .map(day -> day.toUnit(temperatureUnit))
                    .filter(day -> day.max().value() > temperature)
                    .map(
                        day ->
                            new LocationSummaryResponse(
                                locationId,
                                day.date(),
                                day.min().value(),
                                day.max().value(),
                                temperatureUnit.name())));
  }

  @GetMapping("/locations/{locationId}")
  public Mono<List<DayTemperatureResponse>> getLocation(@PathVariable long locationId) {
    return weatherProvider.getForecast(locationId).map(this::toResponse).collectList();
  }

  private DayTemperatureResponse toResponse(DayTemperature day) {
    return new DayTemperatureResponse(
        day.date(), day.min().value(), day.max().value(), day.min().unit().name());
  }

  private TemperatureUnit parseUnit(String unit) {
    try {
      return TemperatureUnit.valueOf(unit.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid unit: " + unit + ". Use CELSIUS or FAHRENHEIT");
    }
  }

  private List<Long> parseLocations(String locations) {
    try {
      return Arrays.stream(locations.split(","))
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .map(Long::parseLong)
          .toList();
    } catch (NumberFormatException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid location IDs: " + locations);
    }
  }
}
