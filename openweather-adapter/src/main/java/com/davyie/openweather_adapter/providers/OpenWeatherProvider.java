package com.davyie.openweather_adapter.providers;

import static com.davyie.TemperatureUnit.CELSIUS;

import com.davyie.InternalUnit;
import com.davyie.TemperatureUnit;
import com.davyie.WeatherProvider;
import com.davyie.filters.RateLimitService;
import com.davyie.models.DayTemperature;
import com.davyie.models.Temperature;
import com.davyie.openweather_adapter.dtos.ForecastResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class OpenWeatherProvider implements WeatherProvider {

  private final String url;
  private final String apiKey;
  private final Duration CACHE_TTL;

  private final ReactiveRedisTemplate<String, ForecastResponse> redisTemplate;
  private final WebClient webClient;

  private final RateLimitService rateLimitService;

  public OpenWeatherProvider(
      WebClient.Builder webClientBuilder,
      ReactiveRedisTemplate<String, ForecastResponse> redisTemplate,
      RateLimitService rateLimitService,
      String apiKey,
      String apiUrl,
      long minutes) {
    this.apiKey = apiKey;
    this.url = apiUrl;
    this.CACHE_TTL = Duration.ofMinutes(minutes);
    this.webClient = webClientBuilder.baseUrl(url).build();
    this.redisTemplate = redisTemplate;
    this.rateLimitService = rateLimitService;
  }

  @Override
  public Mono<List<DayTemperature>> getFiveDaysWeatherForLocation(Integer locationId) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    return fetchWeatherByLocationId(locationId, CELSIUS.toInternal())
        .map(
            resp ->
                resp.list().stream()
                    .map(
                        item -> {
                          LocalDateTime dateTime = LocalDateTime.parse(item.dtTxt(), formatter);
                          LocalDate date = dateTime.toLocalDate();
                          LocalTime time = dateTime.toLocalTime();

                          return new DayTemperature(
                              date,
                              new Temperature(time, CELSIUS, item.main().temp()),
                              resp.city().name());
                        })
                    .toList());
  }

  @Override
  public Flux<DayTemperature> getOneDayWeatherForLocations(
      TemperatureUnit unit, Integer temperature, List<Integer> locations) {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    String tomorrowNoon = LocalDate.now().plusDays(1).toString() + " 12:00:00";

    return Flux.fromIterable(locations)
        .flatMap(locationId -> fetchWeatherByLocationId(locationId, unit.toInternal()))
        .flatMapIterable(
            wr ->
                wr.list().stream()
                    .filter(forecastItem -> forecastItem.dtTxt().equals(tomorrowNoon))
                    .filter(forecastItem -> forecastItem.main().temp() > temperature)
                    .map(
                        forecastItem -> {
                          LocalDateTime datetime =
                              LocalDateTime.parse(forecastItem.dtTxt(), formatter);
                          return new DayTemperature(
                              datetime.toLocalDate(),
                              new Temperature(
                                  datetime.toLocalTime(), unit, forecastItem.main().temp()),
                              wr.city().name());
                        })
                    .toList());
  }

  private Mono<ForecastResponse> fetchWeatherByLocationId(Integer locationId, InternalUnit unit) {
    String cacheKey = "weather:loc:" + locationId + unit.toString();
    return redisTemplate
        .opsForValue()
        .get(cacheKey) // Plan A
        .switchIfEmpty(
            Mono.defer(
                () ->
                    rateLimitService
                        .canCallAPI() // Plan B
                        .flatMap(
                            allowed -> {
                              if (!allowed) {
                                ProblemDetail pd =
                                    ProblemDetail.forStatusAndDetail(
                                        HttpStatus.TOO_MANY_REQUESTS,
                                        "Too many requests towards 3rd party");
                                pd.setTitle("3rd party API rate limit");
                                pd.setProperty("timestamp", LocalTime.now());
                                return Mono.error(
                                    new ErrorResponseException(
                                        HttpStatus.TOO_MANY_REQUESTS, pd, null));
                              }
                              return fetchAndCacheWeatherByLocationId(locationId, unit, cacheKey);
                            })));
  }

  private Mono<ForecastResponse> fetchAndCacheWeatherByLocationId(
      Integer locationId, InternalUnit unit, String cacheKey) {
    return this.webClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/forecast")
                    .queryParam("id", locationId)
                    .queryParam("appid", apiKey)
                    .queryParam("units", unit)
                    .build())
        .retrieve()
        .bodyToMono(ForecastResponse.class)
        .flatMap(
            response ->
                redisTemplate
                    .opsForValue()
                    .set(cacheKey, response, CACHE_TTL)
                    .thenReturn(response));
  }
}
