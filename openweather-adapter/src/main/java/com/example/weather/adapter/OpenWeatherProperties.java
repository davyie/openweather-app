package com.example.weather.adapter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weather")
public class OpenWeatherProperties {

  private String apiKey;
  private String baseUrl = "https://api.openweathermap.org";
  private int cacheTtlMinutes = 10;

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public int getCacheTtlMinutes() {
    return cacheTtlMinutes;
  }

  public void setCacheTtlMinutes(int cacheTtlMinutes) {
    this.cacheTtlMinutes = cacheTtlMinutes;
  }
}
