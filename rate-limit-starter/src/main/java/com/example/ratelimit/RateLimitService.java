package com.example.ratelimit;

import reactor.core.publisher.Mono;

public interface RateLimitService {

  /**
   * Attempt to consume one API call quota. Returns true if allowed, false if daily limit reached.
   */
  Mono<Boolean> tryConsumeApiCall();
}
