package com.back.global.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "custom.redis")
@Component
@Getter
@Setter
public class RedisProperty {

    private String refreshTokenKeyPrefix;
}
