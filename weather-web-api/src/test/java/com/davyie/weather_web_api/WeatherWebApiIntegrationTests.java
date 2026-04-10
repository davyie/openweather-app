package com.davyie.weather_web_api;

import static org.mockito.Mockito.*;

import com.davyie.TemperatureUnit;
import com.davyie.WeatherProvider;
import com.davyie.models.DayTemperature;
import com.davyie.models.Temperature;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class WeatherWebApiIntegrationTests {

  @Autowired private WebTestClient webTestClient;

  @MockitoBean private WeatherProvider weatherProvider;

  // Mock the ProxyManager interface instead of the concrete Lettuce class
  @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
  private ProxyManager<String> lettuceProxyManager;

  // Mock the Reactive template
  @MockitoBean private ReactiveRedisTemplate<String, Integer> reactiveRedisTemplate;

  @MockitoBean private ConsumptionProbe mockProbe = ConsumptionProbe.consumed(1, 1);

  @BeforeEach
  void setUp() {
    // This handles the chain: proxyManager.builder().build(...)
    // Mockito will automatically try to return a mock that fits the method signature
    BucketProxy mockBucket =
        lettuceProxyManager.builder().build((String) any(), (Supplier<BucketConfiguration>) any());

    when(mockBucket.tryConsumeAndReturnRemaining(1)).thenReturn(mockProbe);
    when(mockProbe.isConsumed()).thenReturn(true);
  }

  /**
   * Test hello world endpoint such that our controller is working and we can make a request with
   * webTestClient
   */
  @Test
  void helloWorldEndpointTest() {
    webTestClient.get().uri("/weather/hello").exchange().expectStatus().isOk();
  }

  /**
   * Test the whole chain. When we make a call to the controller it should invoke provider's methods
   * and return a response. Request -> WeatherController -> (invoke) provider -> mocked provider
   * with when() -> send response to Controller -> Send response to webTestClient.
   */
  @Test
  void shouldFetchWeatherFromAdapterAndReturnToClient() {
    when(weatherProvider.getFiveDaysWeatherForLocation(123))
        .thenReturn(Mono.just(List.of(createMockDayTemperature())));
    // --- ACT & ASSERT ---
    // Call the real Controller
    webTestClient
        .get()
        .uri("/weather/locations/123")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].locationName")
        .isEqualTo("Stockholm");
  }

  private DayTemperature createMockDayTemperature() {
    Temperature temperature = new Temperature(LocalTime.now(), TemperatureUnit.CELSIUS, 10);
    DayTemperature dayTemperature =
        new DayTemperature(LocalDate.now().plusDays(1), temperature, "Stockholm");
    return dayTemperature;
  }
}
