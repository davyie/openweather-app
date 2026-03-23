package com.example.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class RateLimitFilter implements WebFilter {

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final int numberOfTokens;
  private final int durationMinutes;

  public RateLimitFilter(int numberOfTokens, int durationMinutes) {
    this.numberOfTokens = numberOfTokens;
    this.durationMinutes = durationMinutes;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String clientIp = getClientIp(exchange);
    Bucket bucket = buckets.computeIfAbsent(clientIp, k -> createBucket());

    if (bucket.tryConsume(1)) {
      return chain.filter(exchange);
    }

    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    return exchange.getResponse().setComplete();
  }

  private Bucket createBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(numberOfTokens)
            .refillGreedy(numberOfTokens, Duration.ofMinutes(durationMinutes))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  private String getClientIp(ServerWebExchange exchange) {
    String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isEmpty()) {
      return forwardedFor.split(",")[0].trim();
    }
    var remoteAddress = exchange.getRequest().getRemoteAddress();
    return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
  }
}
