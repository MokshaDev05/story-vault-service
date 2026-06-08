package com.moksha.storyvault.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadingHistoryResponse {

    private Long id;
    private Long storyId;
    private Long userId;
    private String workId;
    private LocalDateTime accessedAt;
    private Integer chapterNumber;
    private String chapterTitle;
    private String chapterUrl;
    private String sourcePlatform;
    private String chapterAo3Id;
    private String readingMode;
    private String eventType;
    private Long sourceAccountId;
}
