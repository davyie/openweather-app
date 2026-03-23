package com.example.weather.api;

import com.example.weather.api.dto.DayTemperatureResponse;
import com.example.weather.api.dto.LocationSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class WeatherControllerTest {

  @Autowired private WebTestClient webTestClient;

  @Test
  void hello_returnsOk() {
    webTestClient
        .get()
        .uri("/weather/hello")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("OK");
  }

  @Test
  void getLocation_returns5DayForecast() {
    webTestClient
        .get()
        .uri("/weather/locations/2345")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(DayTemperatureResponse.class)
        .hasSize(5);
  }

  @Test
  void summary_returnsLocationsAboveThreshold() {
    webTestClient
        .get()
        .uri("/weather/summary?unit=CELSIUS&temperature=20&locations=2345,1456,7653")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(LocationSummaryResponse.class)
        .value(
            results -> {
              for (LocationSummaryResponse result : results) {
                assert result.maxTemperature() > 20;
              }
            });
  }

  @Test
  void summary_invalidUnit_returnsBadRequest() {
    webTestClient
        .get()
        .uri("/weather/summary?unit=KELVIN&temperature=20&locations=2345")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void summary_invalidLocationId_returnsBadRequest() {
    webTestClient
        .get()
        .uri("/weather/summary?unit=CELSIUS&temperature=20&locations=abc")
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void getLocation_noLocationsAboveHighThreshold_returnsEmptyList() {
    webTestClient
        .get()
        .uri("/weather/summary?unit=CELSIUS&temperature=100&locations=2345,1456")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBodyList(LocationSummaryResponse.class)
        .hasSize(0);
  }
}
