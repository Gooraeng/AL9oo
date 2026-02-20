package com.back.global.external.video.factory;

import com.back.global.external.video.client.VideoClient;
import com.back.global.external.video.enums.VideoProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class VideoClientFactory {

    private final Map<VideoProvider, VideoClient> clientMap;

    // Spring이 VideoClientBase 구현체들을 자동으로 주입
    public VideoClientFactory(List<VideoClient> clients) {
        this.clientMap = clients.stream()
                .collect(Collectors.toMap(
                        VideoClient::getProvider, // 각 클라이언트가 자신의 provider를 반환
                        client -> client
                ));
    }

    public VideoClient getClient(VideoProvider provider) {
        VideoClient client = clientMap.get(provider);
        if (client == null) {
            throw new IllegalArgumentException("No client for provider: " + provider);
        }
        return client;
    }
}