package com.davyie.rate_limit_starter;

import static org.mockito.Mockito.*;

import com.davyie.filters.CacheType;
import com.davyie.filters.RateLimitService;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
public class RateLimitServiceTest {

  @Mock private ReactiveRedisTemplate<String, Integer> redisTemplate;
  @Mock private ReactiveValueOperations<String, Integer> valueOperations;
  @Mock private ProxyManager<String> proxyManager;

  private RateLimitService rateLimitService;

  @BeforeEach
  void setUp() {
    // Wire up the mock operations to the mock template
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    rateLimitService =
        new RateLimitService(10, 1, proxyManager, CacheType.CONCURRENTMAP, redisTemplate, 100, 30);
  }

  @Test
  void canCallAPI_ShouldReturnFalse_WhenLimitReached() {
    String key = "weather_api_limit: " + LocalDate.now().toString();

    // Simulate Redis returning a count equal to the limit
    when(valueOperations.get(key)).thenReturn(Mono.just(100));

    StepVerifier.create(rateLimitService.canCallAPI()).expectNext(false).verifyComplete();

    // Verify increment was NEVER called because the limit was reached
    verify(valueOperations, never()).increment(key);
  }

  @Test
  void canCallAPI_ShouldReturnTrueAndSetExpiry_OnFirstCall() {
    String key = "weather_api_limit: " + LocalDate.now().toString();

    // Simulate first call (key doesn't exist or is 0)
    when(valueOperations.get(key)).thenReturn(Mono.empty());
    when(valueOperations.increment(key)).thenReturn(Mono.just(1L));
    when(redisTemplate.expire(key, Duration.ofMinutes(30))).thenReturn(Mono.just(true));

    StepVerifier.create(rateLimitService.canCallAPI()).expectNext(true).verifyComplete();

    verify(redisTemplate).expire(key, Duration.ofMinutes(30));
  }
}
