package com.back.global.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "custom.site.active")
@Getter
@Setter
public class UrlProperty {

    private String domain;
    private String frontUrl;
    private String backUrl;
}
