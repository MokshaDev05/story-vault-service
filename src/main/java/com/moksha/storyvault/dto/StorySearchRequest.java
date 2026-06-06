package com.moksha.storyvault.dto;

import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.ReadingStatus;
import com.moksha.storyvault.model.enums.StoryStatus;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorySearchRequest {

    // ── Metadata ──────────────────────────────────────────────────────────────
    private String titleContains;
    private String authorContains;
    private String fandomContains;
    private Platform platform;
    private StoryStatus status;
    private Rating rating;
    private ReadingStatus readingStatus;
    private String language;

    // ── Tags & ships ──────────────────────────────────────────────────────────
    private String tagContains;
    private String relationshipContains;
    private String characterContains;

    // ── Counts ────────────────────────────────────────────────────────────────
    private Integer minWordCount;
    private Integer maxWordCount;
    private Integer minChapters;
    private Integer maxChapters;

    // ── AO3 dates ─────────────────────────────────────────────────────────────
    private LocalDate publishedAfter;
    private LocalDate publishedBefore;
    private LocalDate updatedAfter;
    private LocalDate updatedBefore;

    // ── Reading history ───────────────────────────────────────────────────────
    private LocalDate lastAccessedAfter;
    private LocalDate lastAccessedBefore;
    private LocalDate firstAccessedAfter;
    private LocalDate firstAccessedBefore;
    private Integer minAccessCount;
    private Integer chapterAccessed;

    // ── Sort ──────────────────────────────────────────────────────────────────
    @Builder.Default
    private SortField sortBy = SortField.LAST_ACCESSED;

    @Builder.Default
    private String sortDir = "desc";

    public enum SortField {
        TITLE, AUTHOR, FANDOM, LAST_ACCESSED, FIRST_ACCESSED,
        ACCESS_COUNT, WORD_COUNT, CHAPTER_COUNT,
        AO3_PUBLISHED_DATE, AO3_UPDATED_DATE, CREATED_AT
    }
}
