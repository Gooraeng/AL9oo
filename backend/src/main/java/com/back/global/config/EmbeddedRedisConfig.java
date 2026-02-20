package com.back.global.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.Socket;

@Slf4j
@Profile("!prod") // prod 프로필 제외하고 활성화
@Configuration
public class EmbeddedRedisConfig {

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() throws IOException {
        // 포트가 이미 사용 중인지 확인
        redisServer = new RedisServer();

        int redisPort;

        if (AppConfig.isDev()) redisPort = 6380;
        else redisPort = 6381;

        if (isPortInUse(redisPort)) {
            log.info("Port {} is already in use. Skipping embedded Redis startup (external Redis may be running)", redisPort);
            return;
        }

        try {
            redisServer.start();
            log.info("Embedded Redis started on port {}", redisPort);
        } catch (Exception e) {
            log.error("Failed to start embedded Redis server", e);
            throw e;
        }
    }

    @PreDestroy
    public void stopRedis() throws IOException {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
            log.info("Embedded Redis stopped");
        }
    }

    /**
     * 포트 사용 여부 확인
     */
    private boolean isPortInUse(int port) {
        try (Socket _ = new Socket("localhost", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
