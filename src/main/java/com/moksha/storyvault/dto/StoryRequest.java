package com.moksha.storyvault.dto;

import com.moksha.storyvault.model.enums.KudosStatus;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.ReadingStatus;
import com.moksha.storyvault.model.enums.StoryStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    @NotBlank(message = "Author is required")
    @Size(max = 255, message = "Author must not exceed 255 characters")
    private String author;

    @NotBlank(message = "Fandom is required")
    @Size(max = 255, message = "Fandom must not exceed 255 characters")
    private String fandom;

    @NotNull(message = "Platform is required")
    private Platform platform;

    private StoryStatus status;

    private Rating rating;

    private String summary;

    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    private String originalUrl;

    @Size(max = 100, message = "Source work ID must not exceed 100 characters")
    private String sourceWorkId;

    @Min(value = 0, message = "Word count must be zero or greater")
    private Integer wordCount;

    /** Published chapter count. */
    @Min(value = 0, message = "Chapter count must be zero or greater")
    private Integer chapterCount;

    /** Planned total chapters; null means unknown. */
    @Min(value = 0, message = "Total chapters must be zero or greater")
    private Integer totalChapters;

    private LocalDate ao3PublishedDate;

    private LocalDate ao3UpdatedDate;

    @Size(max = 100, message = "Language must not exceed 100 characters")
    private String language;

    private LocalDate completedAt;

    private Set<String> tags;

    private List<String> relationships;

    private List<String> characters;

    private List<String> archiveWarnings;

    private List<String> categories;

    private ReadingStatus readingStatus;

    @Min(value = 0, message = "Current chapter must be zero or greater")
    private Integer currentChapter;

    @Size(max = 2048, message = "Chapter URL must not exceed 2048 characters")
    private String currentChapterUrl;

    private KudosStatus kudosStatus;

    private Long sourceAccountId;
}
