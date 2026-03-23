package com.example.ratelimit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@AutoConfiguration
@ConditionalOnProperty(name = "ratelimit.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

  @Bean
  public RateLimitFilter rateLimitFilter(RateLimitProperties properties) {
    return new RateLimitFilter(properties.getNumberOfTokens(), properties.getDuration());
  }

  @Bean
  @ConditionalOnProperty(
      name = "ratelimit.cache-type",
      havingValue = "REDIS",
      matchIfMissing = true)
  public RateLimitService redisRateLimitService(
      ReactiveStringRedisTemplate redisTemplate, RateLimitProperties properties) {
    return new RedisRateLimitService(redisTemplate, properties.getDailyLimit());
  }

  @Bean
  @ConditionalOnProperty(name = "ratelimit.cache-type", havingValue = "CONCURRENTMAP")
  public RateLimitService inMemoryRateLimitService(RateLimitProperties properties) {
    return new InMemoryRateLimitService(properties.getDailyLimit());
  }
}
