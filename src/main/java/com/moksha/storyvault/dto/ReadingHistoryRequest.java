package com.moksha.storyvault.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadingHistoryRequest {

    private Integer chapterNumber;
    private String chapterTitle;
    private String chapterUrl;
    private String sourcePlatform;
    private String chapterAo3Id;
    private String readingMode;
    private String eventType;
    private Long sourceAccountId;
}
