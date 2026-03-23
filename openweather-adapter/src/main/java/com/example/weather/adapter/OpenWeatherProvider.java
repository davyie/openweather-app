package com.example.weather.adapter;

import com.example.ratelimit.RateLimitService;
import com.example.weather.adapter.dto.ForecastItem;
import com.example.weather.adapter.dto.ForecastResponse;
import com.example.weather.core.WeatherProvider;
import com.example.weather.core.model.DayTemperature;
import com.example.weather.core.model.Temperature;
import com.example.weather.core.model.TemperatureUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class OpenWeatherProvider implements WeatherProvider {

  private static final Logger log = LoggerFactory.getLogger(OpenWeatherProvider.class);
  private static final String CACHE_KEY_PREFIX = "weather:forecast:";
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final WebClient webClient;
  private final RateLimitService rateLimitService;
  private final ReactiveRedisOperations<String, String> redisTemplate;
  private final OpenWeatherProperties properties;
  private final ObjectMapper objectMapper;

  public OpenWeatherProvider(
      WebClient webClient,
      RateLimitService rateLimitService,
      ReactiveRedisOperations<String, String> redisTemplate,
      OpenWeatherProperties properties) {
    this.webClient = webClient;
    this.rateLimitService = rateLimitService;
    this.redisTemplate = redisTemplate;
    this.properties = properties;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Override
  public Flux<DayTemperature> getForecast(long locationId) {
    String cacheKey = CACHE_KEY_PREFIX + locationId;

    return redisTemplate
        .opsForValue()
        .get(cacheKey)
        .flatMapMany(
            cached -> {
              log.debug("Cache hit for location {}", locationId);
              return Flux.fromIterable(deserialize(cached));
            })
        .switchIfEmpty(
            rateLimitService
                .tryConsumeApiCall()
                .flatMapMany(
                    allowed -> {
                      if (!allowed) {
                        log.warn("Daily API rate limit exceeded");
                        return Flux.error(
                            new ResponseStatusException(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Weather service temporarily unavailable due to API rate limits"));
                      }
                      return fetchFromApiAndCache(locationId, cacheKey);
                    }));
  }

  private Flux<DayTemperature> fetchFromApiAndCache(long locationId, String cacheKey) {
    return webClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/data/2.5/forecast")
                    .queryParam("id", locationId)
                    .queryParam("appid", properties.getApiKey())
                    .queryParam("units", "metric")
                    .build())
        .retrieve()
        .onStatus(
            status -> status.value() == 404,
            response ->
                Mono.error(
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Location " + locationId + " not found")))
        .onStatus(
            status -> status.value() == 401,
            response ->
                Mono.error(
                    new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Invalid API key")))
        .bodyToMono(ForecastResponse.class)
        .flatMapMany(
            response -> {
              List<DayTemperature> forecast = aggregateByDay(response.list());
              return cacheResult(cacheKey, forecast).thenMany(Flux.fromIterable(forecast));
            });
  }

  private List<DayTemperature> aggregateByDay(List<ForecastItem> items) {
    Map<LocalDate, List<ForecastItem>> byDay =
        items.stream()
            .collect(
                Collectors.groupingBy(
                    item -> LocalDate.parse(item.dtTxt().substring(0, 10), DATE_FORMATTER)));

    return byDay.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry -> {
              LocalDate date = entry.getKey();
              List<ForecastItem> dayItems = entry.getValue();
              double minTemp =
                  dayItems.stream().mapToDouble(i -> i.main().tempMin()).min().orElse(0);
              double maxTemp =
                  dayItems.stream().mapToDouble(i -> i.main().tempMax()).max().orElse(0);
              return new DayTemperature(
                  date,
                  new Temperature(minTemp, TemperatureUnit.CELSIUS),
                  new Temperature(maxTemp, TemperatureUnit.CELSIUS));
            })
        .sorted(Comparator.comparing(DayTemperature::date))
        .limit(5)
        .collect(Collectors.toList());
  }

  private Mono<Boolean> cacheResult(String cacheKey, List<DayTemperature> forecast) {
    try {
      String json = objectMapper.writeValueAsString(forecast);
      return redisTemplate
          .opsForValue()
          .set(cacheKey, json, Duration.ofMinutes(properties.getCacheTtlMinutes()));
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize forecast for caching", e);
      return Mono.just(false);
    }
  }

  private List<DayTemperature> deserialize(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<DayTemperature>>() {});
    } catch (JsonProcessingException e) {
      log.warn("Failed to deserialize cached forecast", e);
      return List.of();
    }
  }
}
