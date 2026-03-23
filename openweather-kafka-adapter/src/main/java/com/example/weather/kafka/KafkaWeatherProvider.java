package com.example.weather.kafka;

import com.example.ratelimit.RateLimitService;
import com.example.weather.adapter.OpenWeatherProperties;
import com.example.weather.adapter.dto.ForecastItem;
import com.example.weather.adapter.dto.ForecastResponse;
import com.example.weather.core.WeatherProvider;
import com.example.weather.core.model.DayTemperature;
import com.example.weather.core.model.Temperature;
import com.example.weather.core.model.TemperatureUnit;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class KafkaWeatherProvider implements WeatherProvider {

  private static final Logger log = LoggerFactory.getLogger(KafkaWeatherProvider.class);
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final WebClient webClient;
  private final RateLimitService rateLimitService;
  private final OpenWeatherProperties properties;
  private final KafkaAdapterProperties kafkaProperties;
  private final ForecastEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;
  private final ConcurrentHashMap<Long, CacheEntry> localCache = new ConcurrentHashMap<>();

  private record CacheEntry(List<DayTemperature> forecast, Instant fetchedAt) {}

  public KafkaWeatherProvider(
      WebClient webClient,
      RateLimitService rateLimitService,
      OpenWeatherProperties properties,
      KafkaAdapterProperties kafkaProperties,
      ForecastEventPublisher eventPublisher) {
    this.webClient = webClient;
    this.rateLimitService = rateLimitService;
    this.properties = properties;
    this.kafkaProperties = kafkaProperties;
    this.eventPublisher = eventPublisher;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @Override
  public Flux<DayTemperature> getForecast(long locationId) {
    CacheEntry entry = localCache.get(locationId);
    if (entry != null && !isExpired(entry)) {
      log.debug("Local cache hit for location {}", locationId);
      return Flux.fromIterable(entry.forecast());
    }

    return rateLimitService
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
              return fetchFromApiAndPublish(locationId);
            });
  }

  @KafkaListener(
      topics = "${weather.kafka.topic:weather-forecasts}",
      groupId = "${weather.kafka.consumer-group-id:weather-app}")
  public void onForecastEvent(String message) {
    try {
      WeatherForecastEvent event = objectMapper.readValue(message, WeatherForecastEvent.class);
      localCache.put(event.locationId(), new CacheEntry(event.forecast(), Instant.now()));
      log.debug("Received and cached forecast for location {} from Kafka", event.locationId());
    } catch (JsonProcessingException e) {
      log.warn("Failed to deserialize forecast event from Kafka", e);
    }
  }

  private boolean isExpired(CacheEntry entry) {
    return entry
        .fetchedAt()
        .plusSeconds(properties.getCacheTtlMinutes() * 60L)
        .isBefore(Instant.now());
  }

  private Flux<DayTemperature> fetchFromApiAndPublish(long locationId) {
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
              updateCacheAndPublish(locationId, forecast);
              return Flux.fromIterable(forecast);
            });
  }

  private void updateCacheAndPublish(long locationId, List<DayTemperature> forecast) {
    localCache.put(locationId, new CacheEntry(forecast, Instant.now()));
    try {
      String json = objectMapper.writeValueAsString(new WeatherForecastEvent(locationId, forecast));
      eventPublisher.publish(String.valueOf(locationId), json);
      log.debug(
          "Published forecast for location {} to Kafka topic {}",
          locationId,
          kafkaProperties.getTopic());
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize forecast for Kafka publishing", e);
    }
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
}
