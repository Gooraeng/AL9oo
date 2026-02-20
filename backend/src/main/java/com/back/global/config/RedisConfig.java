package com.back.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableRedisRepositories(basePackages = "com.back")
@RequiredArgsConstructor
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();

        // Key는 String으로 직렬화
        StringRedisSerializer keySerializer = new StringRedisSerializer();

        // Value는 JDK 기본 직렬화 사용
        // OAuth2AuthorizationRequest 같은 Spring Security 객체는
        // Java Serializable을 구현하고 있어 JDK 직렬화가 안전함
        JdkSerializationRedisSerializer valueSerializer = new JdkSerializationRedisSerializer();

        // Options
        redisTemplate.setConnectionFactory(connectionFactory);

        // Key serializers
        redisTemplate.setKeySerializer(keySerializer);
        redisTemplate.setHashKeySerializer(keySerializer);

        // Value serializers (JDK Serialization)
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);

        // redisTemplate.setEnableTransactionSupport(true);

        return redisTemplate;
    }
}
