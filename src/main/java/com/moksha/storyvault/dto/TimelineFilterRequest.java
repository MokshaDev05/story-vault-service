package com.moksha.storyvault.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TimelineFilterRequest {

    private LocalDate fromDate;
    private LocalDate toDate;

    /** Optional list of TimelineEventType names to include. Null or empty means all. */
    private List<String> eventTypes;

    /** Filter to events whose story fandom contains this string (case-insensitive). */
    private String fandom;

    /** Filter to events whose story belongs to this collection/shelf. */
    private Long collectionId;

    /** Filter to events whose story has this label. */
    private Long labelId;

    /** Full-text search across metadata (story title, note preview, label/collection names). */
    private String search;

    @Min(0)
    private int page = 0;

    @Min(1)
    @Max(200)
    private int size = 50;
}
