package com.davyie.starter;

import com.davyie.TemperatureUnit;
import com.davyie.WeatherProvider;
import com.davyie.filters.RateLimitService;
import com.davyie.models.DayTemperature;
import com.davyie.models.Temperature;
import com.davyie.openweather_adapter.dtos.ForecastResponse;
import com.davyie.openweather_adapter.providers.OpenWeatherProvider;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AutoConfiguration
@EnableConfigurationProperties(WeatherProperties.class)
public class WeatherAutoConfiguration {

  /**
   * CONFIGURATION FOR OPENWEATHER This bean is ONLY created if: 1. The property 'weather.provider'
   * is set to 'openweathermap' (or is missing, as it's the default match) 2. The
   * OpenWeatherMapProvider class is present on the classpath.
   */
  @Bean
  @ConditionalOnProperty(
      name = "weather.provider",
      havingValue = "openweather",
      matchIfMissing = true)
  @ConditionalOnClass(OpenWeatherProvider.class)
  public WeatherProvider openWeatherMapProvider(
      WeatherProperties properties,
      WebClient.Builder builder,
      ReactiveRedisTemplate<String, ForecastResponse> reactiveRedisTemplate,
      RateLimitService rateLimitService) {
    return new OpenWeatherProvider(
        builder,
        reactiveRedisTemplate,
        rateLimitService,
        properties.getApiKey(),
        properties.getApiUrl(),
        properties.getCacheTtlMinutes());
  }

  @Bean
  public ReactiveRedisTemplate<String, ForecastResponse> reactiveRedisTemplate(
      ReactiveRedisConnectionFactory factory) {

    // Use the new JacksonJsonRedisSerializer (without the '2')
    JacksonJsonRedisSerializer<ForecastResponse> valueSerializer =
        new JacksonJsonRedisSerializer<>(ForecastResponse.class);

    // Build the context as before
    RedisSerializationContext<String, ForecastResponse> context =
        RedisSerializationContext.<String, ForecastResponse>newSerializationContext(
                new StringRedisSerializer())
            .value(valueSerializer)
            .hashKey(new StringRedisSerializer())
            .hashValue(valueSerializer)
            .build();

    return new ReactiveRedisTemplate<>(factory, context);
  }

  /**
   * CONFIGURATION FOR A "FAKE" PROVIDER (For Testing) This bean is ONLY created if
   * 'weather.provider' is set to 'fake'.
   */
  @Bean
  @ConditionalOnProperty(name = "weather.provider", havingValue = "fake")
  public WeatherProvider fakeWeatherProvider() {
    return new WeatherProvider() {
      @Override
      public Mono<List<DayTemperature>> getFiveDaysWeatherForLocation(Integer locationId) {
        DayTemperature winterHold =
            new DayTemperature(
                LocalDate.of(2025, 12, 21),
                new Temperature(LocalTime.of(18, 0), TemperatureUnit.CELSIUS, 0.0),
                "Stockholm");
        DayTemperature summerPeak =
            new DayTemperature(
                LocalDate.of(2025, 7, 15),
                new Temperature(LocalTime.of(14, 30), TemperatureUnit.CELSIUS, 25),
                "Gothenburg");
        List<DayTemperature> temps = new ArrayList<>();
        temps.add(winterHold);
        temps.add(summerPeak);
        return Mono.just(temps);
      }

      @Override
      public Flux<DayTemperature> getOneDayWeatherForLocations(
          TemperatureUnit unit, Integer temperature, List<Integer> locations) {
        DayTemperature winterHold =
            new DayTemperature(
                LocalDate.of(2025, 12, 21),
                new Temperature(LocalTime.of(18, 0), TemperatureUnit.CELSIUS, 0.0),
                "Stockholm");
        DayTemperature summerPeak =
            new DayTemperature(
                LocalDate.of(2026, 7, 15),
                new Temperature(LocalTime.of(14, 30), TemperatureUnit.CELSIUS, 25),
                "Gothenburg");
        List<DayTemperature> temps = new ArrayList<>();
        temps.add(winterHold);
        temps.add(summerPeak);
        List<List<DayTemperature>> list = new ArrayList<>();
        list.add(temps);

        return Flux.just(winterHold, summerPeak);
      }
    };
  }
}
