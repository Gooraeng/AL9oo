package com.back.global.external.video.client;

import com.back.global.external.video.dto.VideoStatusDto;
import com.back.global.external.video.enums.VideoProvider;

import java.util.List;

public interface VideoClient {

    List<VideoStatusDto> checkVideoStatus(List<String> videoIds);
    VideoProvider getProvider();
}
