package com.davyie.filters;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RateLimitFilter implements Filter {

  private final int consumedToken;
  private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
  private final RateLimitService rateLimitService;

  public RateLimitFilter(int consumedToken, RateLimitService rateLimitService) {
    this.consumedToken = consumedToken;
    this.rateLimitService = rateLimitService;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    String clientIp = httpRequest.getRemoteAddr();
    Bucket bucket = rateLimitService.resolveBucket(clientIp);

    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(consumedToken);

    if (probe.isConsumed()) {
      httpResponse.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
      chain.doFilter(request, response);
    } else {
      long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
      httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      httpResponse.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
      httpResponse
          .getWriter()
          .write("Too many requests. Please try again in " + waitForRefill + " seconds.");
    }
  }
}
