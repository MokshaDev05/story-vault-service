package com.moksha.storyvault.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ReadingHistoryStats {
    private Long storyId;
    private LocalDateTime firstAccessedAt;
    private Long accessCount;
}
