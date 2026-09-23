package com.iplay.backend.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis is a cache, so a Redis outage must never take the app down: the same
 * data is always available from MySQL. Two settings enforce that.
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

    /**
     * The default handler rethrows cache errors, turning a cache miss into a
     * failed request. This one logs and falls through to the database instead.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /**
     * By default Lettuce queues commands while it reconnects, so a request can
     * block for as long as the outage lasts. Rejecting them instead means a
     * request fails in milliseconds and the cache error handler takes over.
     */
    @Bean
    LettuceClientConfigurationBuilderCustomizer lettuceClientCustomizer() {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }
}
