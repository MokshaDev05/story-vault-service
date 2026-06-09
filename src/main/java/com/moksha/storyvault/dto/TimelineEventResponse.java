package com.moksha.storyvault.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TimelineEventResponse {

    private Long id;
    private String eventType;
    private LocalDateTime eventTimestamp;
    private Long storyId;
    private String storyTitle;
    private String storyFandom;
    private String metadata;
    private LocalDateTime createdAt;
}
