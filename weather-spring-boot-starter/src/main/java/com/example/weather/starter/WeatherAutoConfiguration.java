package com.example.weather.starter;

import com.example.ratelimit.RateLimitService;
import com.example.weather.adapter.OpenWeatherProperties;
import com.example.weather.adapter.OpenWeatherProvider;
import com.example.weather.core.WeatherProvider;
import com.example.weather.kafka.ForecastEventPublisher;
import com.example.weather.kafka.KafkaAdapterProperties;
import com.example.weather.kafka.KafkaWeatherProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
@EnableConfigurationProperties({
  WeatherProperties.class,
  OpenWeatherProperties.class,
  KafkaAdapterProperties.class
})
public class WeatherAutoConfiguration {

  @Bean
  @ConditionalOnProperty(
      name = "weather.provider",
      havingValue = "openweather",
      matchIfMissing = true)
  @ConditionalOnMissingBean(WeatherProvider.class)
  public WeatherProvider openWeatherProvider(
      WeatherProperties weatherProperties,
      OpenWeatherProperties openWeatherProperties,
      RateLimitService rateLimitService,
      ReactiveRedisOperations<String, String> redisTemplate) {
    WebClient webClient = WebClient.builder().baseUrl(weatherProperties.getBaseUrl()).build();
    return new OpenWeatherProvider(
        webClient, rateLimitService, redisTemplate, openWeatherProperties);
  }

  @Bean
  @ConditionalOnProperty(name = "weather.provider", havingValue = "openweather-kafka")
  @ConditionalOnMissingBean(WeatherProvider.class)
  public KafkaWeatherProvider kafkaWeatherProvider(
      WeatherProperties weatherProperties,
      OpenWeatherProperties openWeatherProperties,
      KafkaAdapterProperties kafkaAdapterProperties,
      RateLimitService rateLimitService,
      KafkaTemplate<String, String> kafkaTemplate) {
    WebClient webClient = WebClient.builder().baseUrl(weatherProperties.getBaseUrl()).build();
    ForecastEventPublisher publisher =
        (key, value) -> kafkaTemplate.send(kafkaAdapterProperties.getTopic(), key, value);
    return new KafkaWeatherProvider(
        webClient, rateLimitService, openWeatherProperties, kafkaAdapterProperties, publisher);
  }

  @Bean
  @ConditionalOnProperty(name = "weather.provider", havingValue = "fake")
  @ConditionalOnMissingBean(WeatherProvider.class)
  public WeatherProvider fakeWeatherProvider() {
    return new FakeWeatherProvider();
  }
}
