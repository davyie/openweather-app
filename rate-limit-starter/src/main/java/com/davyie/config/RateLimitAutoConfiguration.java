package com.davyie.config;

import com.davyie.filters.RateLimitFilter;
import com.davyie.filters.RateLimitService;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@AutoConfiguration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(prefix = "ratelimit", name = "enabled", havingValue = "true")
public class RateLimitAutoConfiguration {

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
      RateLimitProperties properties,
      ReactiveRedisTemplate reactiveRateLimitRedisTemplate,
      ProxyManager<String> lettuceProxyManager) {
    FilterRegistrationBean<RateLimitFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(
        new RateLimitFilter(
            properties.getConsumedTokens(),
            rateLimitService(properties, reactiveRateLimitRedisTemplate, lettuceProxyManager)));
    registrationBean.addUrlPatterns(properties.getPattern());
    return registrationBean;
  }

  @Bean
  public RateLimitService rateLimitService(
      RateLimitProperties properties,
      ReactiveRedisTemplate<String, Integer> reactiveRateLimitRedisTemplate,
      ProxyManager<String> lettuceProxyManager) {
    return new RateLimitService(
        properties.getNumberOfTokens(),
        properties.getDuration(),
        lettuceProxyManager,
        properties.getCacheType(),
        reactiveRateLimitRedisTemplate,
        properties.getDailyLimit(),
        properties.getCacheTTL());
  }

  @Bean
  public ProxyManager<String> lettuceProxyManager(RateLimitProperties properties) {
    RedisClient redisClient =
        RedisClient.create(
            "redis://" + properties.getRedisHost() + ":" + properties.getRedisPort());
    StatefulRedisConnection<String, byte[]> connection =
        redisClient.connect(RedisCodec.of(StringCodec.UTF8, new ByteArrayCodec()));

    // Lettuce-based proxy manager for distributed buckets
    return LettuceBasedProxyManager.builderFor(connection)
        .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(Duration.ofMinutes(1)))
        .build();
  }

  @Bean
  public ReactiveRedisTemplate<String, Integer> reactiveRateLimitRedisTemplate(
      ReactiveRedisConnectionFactory factory) {

    // Use the new JacksonJsonRedisSerializer (without the '2')
    JacksonJsonRedisSerializer<Integer> valueSerializer =
        new JacksonJsonRedisSerializer<>(Integer.class);

    // Build the context as before
    RedisSerializationContext<String, Integer> context =
        RedisSerializationContext.<String, Integer>newSerializationContext(
                new StringRedisSerializer())
            .value(valueSerializer)
            .hashKey(new StringRedisSerializer())
            .hashValue(valueSerializer)
            .build();

    return new ReactiveRedisTemplate<>(factory, context);
  }
}
