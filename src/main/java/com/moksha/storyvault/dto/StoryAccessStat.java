package com.moksha.storyvault.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class StoryAccessStat {
    private Long storyId;
    private String storyTitle;
    private Long accessCount;
    private LocalDateTime lastAccessedAt;
}
