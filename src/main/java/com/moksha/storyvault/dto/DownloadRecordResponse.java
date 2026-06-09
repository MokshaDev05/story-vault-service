package com.moksha.storyvault.dto;

import com.moksha.storyvault.model.enums.FileType;
import com.moksha.storyvault.model.enums.Platform;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadRecordResponse {

    private Long id;
    private Long storyId;
    private String storyTitle;
    private String storyFandom;
    private String storyAuthor;
    private String storyOriginalUrl;
    private Platform platform;
    private FileType fileType;
    private String fileName;
    private String storageKey;
    private String sourceUrl;
    private String notes;
    private LocalDateTime downloadedAt;
}
