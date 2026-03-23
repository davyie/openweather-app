package com.example.ratelimit;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import reactor.core.publisher.Mono;

public class InMemoryRateLimitService implements RateLimitService {

  private final ConcurrentHashMap<LocalDate, AtomicLong> counters = new ConcurrentHashMap<>();
  private final int dailyLimit;

  public InMemoryRateLimitService(int dailyLimit) {
    this.dailyLimit = dailyLimit;
  }

  @Override
  public Mono<Boolean> tryConsumeApiCall() {
    LocalDate today = LocalDate.now();
    AtomicLong counter = counters.computeIfAbsent(today, k -> new AtomicLong(0));
    long count = counter.incrementAndGet();
    return Mono.just(count <= dailyLimit);
  }
}
