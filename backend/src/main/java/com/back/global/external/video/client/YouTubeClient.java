package com.back.global.external.video.client;

import com.back.domain.reference.type.ReferenceStatus;
import com.back.global.property.GoogleClientProperty;
import com.back.global.external.video.dto.VideoStatusDto;
import com.back.global.external.video.enums.VideoProvider;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.VideoListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class YouTubeClient implements VideoClient {

    private final GoogleClientProperty googleClientProperty;
    private final YouTube youTube = new YouTube.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                request -> {
                    request.setConnectTimeout(5000) // 연결 타임아웃
                            .setReadTimeout(10000);   // 읽기 타임아웃
                    request.executeAsync();
                })
                .setApplicationName("AL9oo-YouTubeClient")
                .build();

    @Override
    public List<VideoStatusDto> checkVideoStatus(List<String> videoIds) {
        YouTube.Videos.List baseRequest = createBaseRequest();

        baseRequest.setId(videoIds);

        return executeRequest(baseRequest)
                .getItems()
                .stream()
                .map(item -> new VideoStatusDto(
                    item.getId(),
                    ReferenceStatus.forYouTube(item.getStatus().getPrivacyStatus()),
                    VideoProvider.YOUTUBE)
                ).toList();
    }

    @Override
    public VideoProvider getProvider() {
        return VideoProvider.YOUTUBE;
    }

    private VideoListResponse executeRequest(YouTube.Videos.List request) {
        try {
            return request.execute();
        } catch (IOException e) {
            throw new RuntimeException("Failed to execute YouTube API request", e);
        }
    }

    private YouTube.Videos.List createBaseRequest() {
        try {
            return youTube
                    .videos()
                    .list(List.of("status"))
                    .setKey(googleClientProperty.getYouTubeApiKey());
        } catch (IOException e) {
            throw new RuntimeException("Failed to check video status", e);
        }
    }

}
