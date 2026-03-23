package com.example.ratelimit;

import java.time.Duration;
import java.time.LocalDate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

public class RedisRateLimitService implements RateLimitService {

  private static final String KEY_PREFIX = "openweather:daily:calls:";

  private final ReactiveStringRedisTemplate redisTemplate;
  private final int dailyLimit;

  public RedisRateLimitService(ReactiveStringRedisTemplate redisTemplate, int dailyLimit) {
    this.redisTemplate = redisTemplate;
    this.dailyLimit = dailyLimit;
  }

  @Override
  public Mono<Boolean> tryConsumeApiCall() {
    String key = KEY_PREFIX + LocalDate.now();
    return redisTemplate
        .opsForValue()
        .increment(key)
        .flatMap(
            count -> {
              if (count == 1) {
                return redisTemplate
                    .expire(key, Duration.ofDays(1))
                    .thenReturn(count <= dailyLimit);
              }
              return Mono.just(count <= dailyLimit);
            });
  }
}
