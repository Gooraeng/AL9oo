package com.back.global.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "custom.google.client")
@Component
@Getter
@Setter
public class GoogleClientProperty {

    private String id;
    private String secret;
    private String youTubeApiKey;
}
