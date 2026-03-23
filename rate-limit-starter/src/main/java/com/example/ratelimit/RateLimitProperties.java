package com.example.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

  private boolean enabled = true;
  private int numberOfTokens = 10;
  private int duration = 1;
  private int dailyLimit = 3;
  private CacheType cacheType = CacheType.REDIS;

  public enum CacheType {
    REDIS,
    CONCURRENTMAP
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getNumberOfTokens() {
    return numberOfTokens;
  }

  public void setNumberOfTokens(int numberOfTokens) {
    this.numberOfTokens = numberOfTokens;
  }

  public int getDuration() {
    return duration;
  }

  public void setDuration(int duration) {
    this.duration = duration;
  }

  public int getDailyLimit() {
    return dailyLimit;
  }

  public void setDailyLimit(int dailyLimit) {
    this.dailyLimit = dailyLimit;
  }

  public CacheType getCacheType() {
    return cacheType;
  }

  public void setCacheType(CacheType cacheType) {
    this.cacheType = cacheType;
  }
}
