package com.back.global.security.jwt.refreshToken.domain;

public enum DeviceType {
    WEB_DESKTOP,
    WEB_MOBILE,
    ANDROID_APP,
    IOS_APP,
    DISCORD_BOT;

    public static DeviceType of(String deviceType) {
        return DeviceType.valueOf(deviceType.toUpperCase());
    }
}
