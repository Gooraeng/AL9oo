package com.back.global.security.cookie;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
public class BaseCookieValues {

    private String name;
    private Duration duration;
    private String domain;
    private String path;
    private boolean httpOnly;
    private boolean secure;
    private String sameSite;
}
