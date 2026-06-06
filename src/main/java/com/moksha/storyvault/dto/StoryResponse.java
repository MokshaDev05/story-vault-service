package com.moksha.storyvault.dto;

import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.ReadingStatus;
import com.moksha.storyvault.model.enums.StoryStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryResponse {

    private Long id;
    private String title;
    private String author;
    private String fandom;
    private Platform platform;
    private StoryStatus status;
    private Rating rating;
    private String summary;
    private String originalUrl;
    private String sourceWorkId;
    private Integer wordCount;
    private Integer chapterCount;
    private Integer totalChapters;
    private LocalDate ao3PublishedDate;
    private LocalDate ao3UpdatedDate;
    private String language;
    private LocalDate completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<String> tags;
    private List<String> relationships;
    private List<String> characters;
    private List<String> archiveWarnings;
    private List<String> categories;
    private boolean hasFile;
    private ReadingStatus readingStatus;
    private Integer currentChapter;
    private String currentChapterUrl;
    private LocalDateTime lastAccessedAt;
    private LocalDateTime firstAccessedAt;
    private Long accessCount;
}
