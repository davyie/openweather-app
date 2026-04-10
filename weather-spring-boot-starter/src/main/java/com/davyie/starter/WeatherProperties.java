package com.davyie.starter;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Getter
@Setter
@ConfigurationProperties(prefix = "weather")
public class WeatherProperties {

  /** The name of the weather provider to use. Options: "openweathermap" or "fake" */
  private String provider;

  /** The API key for the provider. */
  private String apiKey;

  /** The Base URL (useful for testing or proxying). */
  private String apiUrl;

  private long cacheTtlMinutes;
}
