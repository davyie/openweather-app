package com.example.weather.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ratelimit.RateLimitService;
import com.example.weather.adapter.OpenWeatherProperties;
import com.example.weather.core.model.TemperatureUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class KafkaWeatherProviderTest {

  private MockWebServer mockWebServer;
  private KafkaWeatherProvider provider;
  private ObjectMapper objectMapper;

  @Mock private RateLimitService rateLimitService;
  @Mock private ForecastEventPublisher eventPublisher;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    OpenWeatherProperties properties = new OpenWeatherProperties();
    properties.setApiKey("test-key");
    properties.setBaseUrl(mockWebServer.url("/").toString().replaceAll("/$", ""));
    properties.setCacheTtlMinutes(10);

    KafkaAdapterProperties kafkaProperties = new KafkaAdapterProperties();
    kafkaProperties.setTopic("weather-forecasts");

    WebClient webClient = WebClient.builder().baseUrl(properties.getBaseUrl()).build();

    provider =
        new KafkaWeatherProvider(
            webClient, rateLimitService, properties, kafkaProperties, eventPublisher);

    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void getForecast_returnsAggregatedDailyForecast() {
    when(rateLimitService.tryConsumeApiCall()).thenReturn(Mono.just(true));
    mockWebServer.enqueue(
        new MockResponse()
            .setBody(buildForecastJson())
            .addHeader("Content-Type", "application/json"));

    StepVerifier.create(provider.getForecast(2345L).collectList())
        .assertNext(
            forecast -> {
              assertThat(forecast).isNotEmpty();
              assertThat(forecast.get(0).min().unit()).isEqualTo(TemperatureUnit.CELSIUS);
              assertThat(forecast.get(0).max().value())
                  .isGreaterThanOrEqualTo(forecast.get(0).min().value());
            })
        .verifyComplete();

    verify(eventPublisher).publish(anyString(), anyString());
  }

  @Test
  void getForecast_returnsCachedForecast_withoutApiCall() throws Exception {
    when(rateLimitService.tryConsumeApiCall()).thenReturn(Mono.just(true));
    mockWebServer.enqueue(
        new MockResponse()
            .setBody(buildForecastJson())
            .addHeader("Content-Type", "application/json"));

    provider.getForecast(2345L).collectList().block();

    StepVerifier.create(provider.getForecast(2345L).collectList())
        .assertNext(forecast -> assertThat(forecast).isNotEmpty())
        .verifyComplete();

    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    verify(rateLimitService).tryConsumeApiCall();
  }

  @Test
  void getForecast_kafkaEventPopulatesCache() throws Exception {
    when(rateLimitService.tryConsumeApiCall()).thenReturn(Mono.just(true));
    mockWebServer.enqueue(
        new MockResponse()
            .setBody(buildForecastJson())
            .addHeader("Content-Type", "application/json"));

    List<com.example.weather.core.model.DayTemperature> original =
        provider.getForecast(2345L).collectList().block();

    String eventJson = objectMapper.writeValueAsString(new WeatherForecastEvent(9999L, original));
    provider.onForecastEvent(eventJson);

    StepVerifier.create(provider.getForecast(9999L).collectList())
        .assertNext(forecast -> assertThat(forecast).isEqualTo(original))
        .verifyComplete();

    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    verify(rateLimitService).tryConsumeApiCall();
  }

  @Test
  void getForecast_rateLimitExceeded_returnsError() {
    when(rateLimitService.tryConsumeApiCall()).thenReturn(Mono.just(false));

    StepVerifier.create(provider.getForecast(2345L))
        .expectErrorMatches(
            e ->
                e instanceof org.springframework.web.server.ResponseStatusException
                    && ((org.springframework.web.server.ResponseStatusException) e)
                            .getStatusCode()
                            .value()
                        == 503)
        .verify();

    verify(eventPublisher, never()).publish(anyString(), anyString());
  }

  private String buildForecastJson() {
    return """
        {
          "cod": "200",
          "cnt": 4,
          "list": [
            {
              "dt": 1703332800,
              "main": { "temp": 10.0, "temp_min": 8.0, "temp_max": 12.0 },
              "dt_txt": "2024-01-15 00:00:00"
            },
            {
              "dt": 1703343600,
              "main": { "temp": 14.0, "temp_min": 10.0, "temp_max": 16.0 },
              "dt_txt": "2024-01-15 03:00:00"
            },
            {
              "dt": 1703419200,
              "main": { "temp": 12.0, "temp_min": 9.0, "temp_max": 15.0 },
              "dt_txt": "2024-01-16 00:00:00"
            },
            {
              "dt": 1703430000,
              "main": { "temp": 16.0, "temp_min": 11.0, "temp_max": 18.0 },
              "dt_txt": "2024-01-16 03:00:00"
            }
          ]
        }
        """;
  }
}
