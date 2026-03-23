package com.example.weather.kafka;

@FunctionalInterface
public interface ForecastEventPublisher {

  void publish(String locationKey, String eventJson);
}
