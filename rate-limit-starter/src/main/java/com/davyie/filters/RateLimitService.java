package com.davyie.filters;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RateLimitService {

  private final Logger LOG = LoggerFactory.getLogger(RateLimitService.class);

  private final int numberOfTokens;
  private final long duration;
  private final CacheType cacheType;

  // RateLimitMonitor
  private final ReactiveRedisTemplate<String, Integer> redisTemplate;
  private final int DAILY_LIMIT;
  private final long CACHE_TTL;

  // Cache to store buckets per client (IP/User ID)
  private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
  private ProxyManager<String> proxyManager;

  public RateLimitService(
      int numberOfTokens,
      long duration,
      ProxyManager<String> proxyManager,
      CacheType cacheType,
      ReactiveRedisTemplate<String, Integer> redisTemplate,
      int daily_limit,
      long cacheTTL) {
    this.numberOfTokens = numberOfTokens;
    this.duration = duration;
    this.proxyManager = proxyManager;
    this.cacheType = cacheType;
    this.redisTemplate = redisTemplate;
    this.DAILY_LIMIT = daily_limit;
    this.CACHE_TTL = cacheTTL;
  }

  public Bucket resolveBucket(String key) {
    switch (cacheType) {
      case REDIS -> {
        return resolveBucketWithRedis(key);
      }
      case CONCURRENTMAP -> {
        return resolveBucketWithConcurrentMap(key);
      }
      default -> {
        throw new RuntimeException("CacheType was not specified");
      }
    }
  }

  private Bucket resolveBucketWithRedis(String key) {
    BucketConfiguration bucketConfiguration =
        BucketConfiguration.builder().addLimit(this.getBandwitdh()).build();
    return proxyManager.builder().build(key, () -> bucketConfiguration);
  }

  private Bandwidth getBandwitdh() {
    return Bandwidth.builder()
        .capacity(numberOfTokens)
        .refillGreedy(numberOfTokens, Duration.ofMinutes(duration))
        .build();
  }

  private Bucket resolveBucketWithConcurrentMap(String key) {
    return cache.computeIfAbsent(key, this::newBucket);
  }

  private Bucket newBucket(String key) {
    return Bucket.builder().addLimit(this.getBandwitdh()).build();
  }

  public Mono<Boolean> canCallAPI() {
    String key = "weather_api_limit: " + LocalDate.now().toString();
    return redisTemplate
        .opsForValue()
        .get(key)
        .defaultIfEmpty(0)
        .doOnNext(count -> LOG.info("currentCount: {} and daily limit: {}", count, DAILY_LIMIT))
        .flatMap(
            currentCount -> {
              if (currentCount >= this.DAILY_LIMIT) {
                return Mono.just(false);
              }

              return redisTemplate
                  .opsForValue()
                  .increment(key) // We have budget left, so now we increment
                  .flatMap(
                      newCount -> {
                        if (newCount == 1) {
                          return redisTemplate.expire(key, Duration.ofMinutes(this.CACHE_TTL));
                        }
                        return Mono.just(true);
                      })
                  .thenReturn(true);
            })
        .doOnNext(b -> LOG.info(b ? "Increment success" : "Increment unsuccess"))
        .log("AfterFetch");
  }
}
