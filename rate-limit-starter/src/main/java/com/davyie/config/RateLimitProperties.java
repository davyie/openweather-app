package com.davyie.config;

import com.davyie.filters.CacheType;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@Getter
@Setter
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {
  private int consumedTokens;
  private String pattern;

  private long duration;
  private int numberOfTokens;

  private CacheType cacheType;

  private int dailyLimit;

  private String redisHost;
  private String redisPort;

  private long cacheTTL;
}
