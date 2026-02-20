package com.back.global.external.video.dto;

import com.back.domain.reference.type.ReferenceStatus;
import com.back.global.external.video.enums.VideoProvider;

public record VideoStatusDto(
        String id,
        ReferenceStatus status,
        VideoProvider provider
) {
}
