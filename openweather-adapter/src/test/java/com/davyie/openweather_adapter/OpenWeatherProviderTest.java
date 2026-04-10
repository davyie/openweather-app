package com.davyie.openweather_adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.davyie.InternalUnit;
import com.davyie.TemperatureUnit;
import com.davyie.filters.RateLimitService;
import com.davyie.models.DayTemperature;
import com.davyie.openweather_adapter.dtos.ForecastResponse;
import com.davyie.openweather_adapter.providers.OpenWeatherProvider;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OpenWeatherProviderTest {

  private static MockWebServer mockWebServer;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private ReactiveRedisTemplate<String, ForecastResponse> redisTemplate;
  @Mock private ReactiveValueOperations<String, ForecastResponse> valueOperations;
  @Mock private RateLimitService rateLimitService;

  private OpenWeatherProvider provider;

  @BeforeAll
  static void setUpServer() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @AfterAll
  static void tearDownServer() throws IOException {
    mockWebServer.shutdown();
  }

  @BeforeEach
  void setUp() {
    // Stub the opsForValue() call which is used in the provider
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // We use a real WebClient builder, but point it to our local MockWebServer
    WebClient.Builder webClientBuilder = WebClient.builder();

    provider =
        new OpenWeatherProvider(
            webClientBuilder,
            redisTemplate,
            rateLimitService,
            "dummy-api-key",
            mockWebServer.url("/").toString(),
            10 // minutes cache TTL
            );
  }

  @Test
  void getFiveDaysWeather_whenCacheHit_returnsCachedDataAndSkipsApi() {
    // Arrange
    Integer locationId = 123;
    ForecastResponse cachedResponse = createMockForecastResponse("2026-01-01 12:00:00", 25.0);

    // Cache has the value
    when(valueOperations.get(anyString())).thenReturn(Mono.just(cachedResponse));

    // Act
    Mono<List<DayTemperature>> result = provider.getFiveDaysWeatherForLocation(locationId);

    // Assert
    StepVerifier.create(result)
        .expectNextMatches(list -> list.size() == 1 && list.get(0).temperature().temp() == 25.0)
        .verifyComplete();

    // Verify API was never called via rate limit service check
    verify(rateLimitService, never()).canCallAPI();
  }

  @Test
  void getFiveDaysWeather_whenCacheMissAndRateLimitPassed_callsApiAndCaches() throws Exception {
    // Arrange
    Integer locationId = 123;
    ForecastResponse apiResponse = createMockForecastResponse("2026-01-01 12:00:00", 22.5);

    // Mock server response
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setBody(objectMapper.writeValueAsString(apiResponse)));

    when(valueOperations.get(anyString())).thenReturn(Mono.empty());
    when(valueOperations.set(anyString(), any(ForecastResponse.class), eq(Duration.ofMinutes(10))))
        .thenReturn(Mono.just(true));
    when(rateLimitService.canCallAPI()).thenReturn(Mono.just(true));

    // Act
    Mono<List<DayTemperature>> result = provider.getFiveDaysWeatherForLocation(locationId);

    // Assert
    StepVerifier.create(result)
        .expectNextMatches(list -> list.size() == 1 && list.get(0).temperature().temp() == 22.5)
        .verifyComplete();

    // Verify the external API URI was built correctly
    RecordedRequest request = mockWebServer.takeRequest();
    InternalUnit expectedUnit = TemperatureUnit.CELSIUS.toInternal();
    assertEquals("/forecast?id=123&appid=dummy-api-key&units=" + expectedUnit, request.getPath());

    // Verify cache was populated
    verify(valueOperations).set(anyString(), any(), eq(Duration.ofMinutes(10)));
  }

  @Test
  void getOneDayWeatherForLocations_filtersCorrectly() {
    // Arrange
    Integer locationId = 123;
    TemperatureUnit unit = TemperatureUnit.CELSIUS;

    // Generate tomorrow's date at noon exactly as the logic expects
    String tomorrowNoon = LocalDate.now().plusDays(1).toString() + " 12:00:00";

    // Create mock response using Mockito Deep Stubs for brevity, or manually instantiate your
    // records
    ForecastResponse mockResponse = mock(ForecastResponse.class, RETURNS_DEEP_STUBS);

    var validItem =
        mock(com.davyie.openweather_adapter.dtos.ForecastItem.class, RETURNS_DEEP_STUBS);
    when(validItem.dtTxt()).thenReturn(tomorrowNoon);
    when(validItem.main().temp()).thenReturn(25.0); // Higher than threshold (20)

    when(mockResponse.list()).thenReturn(List.of(validItem));

    when(valueOperations.get(anyString())).thenReturn(Mono.just(mockResponse));

    // Act
    Flux<DayTemperature> result =
        provider.getOneDayWeatherForLocations(unit, 20, List.of(locationId));

    // Assert
    StepVerifier.create(result)
        .expectNextMatches(
            dayTemp ->
                // We check the single object directly now
                dayTemp.temperature().temp() == 25.0
                    && dayTemp.temperature().unit() == unit
                    && dayTemp.date().equals(LocalDate.now().plusDays(1)))
        .verifyComplete();
  }

  // --- Helper Methods ---

  /**
   * Helper to instantiate your DTOs. Note: You will need to adapt this to match your actual
   * record/class constructors.
   */
  private ForecastResponse createMockForecastResponse(String dateTimeText, double temp) {
    // Using Mockito deep stubs here to avoid guessing your class constructors,
    // but it is highly recommended to use `new ForecastItem(...)` directly here.
    ForecastResponse response = mock(ForecastResponse.class, RETURNS_DEEP_STUBS);
    var item = mock(com.davyie.openweather_adapter.dtos.ForecastItem.class, RETURNS_DEEP_STUBS);

    when(item.dtTxt()).thenReturn(dateTimeText);
    when(item.main().temp()).thenReturn(temp);
    when(response.list()).thenReturn(List.of(item));

    return response;
  }
}
