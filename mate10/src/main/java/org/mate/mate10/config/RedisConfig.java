package org.mate.mate10.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {
    private GenericJacksonJsonRedisSerializer typedJsonSerializer(){
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("org.mate.mate10.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .build();
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(validator)
                .build();
    }
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJacksonJsonRedisSerializer serializer = typedJsonSerializer();
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(typedJsonSerializer()))
                .disableCachingNullValues();

        // 按缓存名定制 TTL
        Map<String, RedisCacheConfiguration> initialCaches = Map.of(
                "qcStats",  config.entryTtl(Duration.ofMinutes(5)),
                "qcDetail", config.entryTtl(Duration.ofMinutes(30))
        );

        // immediateWrites：强制所有缓存写入（含 clear）立即同步执行，
        // 修复 Spring Data Redis 4.0 默认批量写入导致 clear() 静默失效
        RedisCacheWriter cacheWriter = RedisCacheWriter.create(connectionFactory,
                builder -> builder.immediateWrites());

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(config)
                .withInitialCacheConfigurations(initialCaches)
                .transactionAware()
                .build();
    }

}
