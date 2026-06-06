package com.moksha.storyvault.dto;

import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.StoryStatus;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

// Safe for unauthenticated access: no file data, no notes, no storage paths, no download records
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryPublicResponse {

    private Long id;
    private String title;
    private String author;
    private String fandom;
    private Platform platform;
    private StoryStatus status;
    private Rating rating;
    private String summary;
    private String originalUrl;
    private Integer wordCount;
    private Integer chapterCount;
    private Integer totalChapters;
    private LocalDate completedAt;
    private Set<String> tags;
    private List<String> relationships;
}
