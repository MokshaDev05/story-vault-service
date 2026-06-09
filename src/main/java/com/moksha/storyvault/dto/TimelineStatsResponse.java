package com.moksha.storyvault.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TimelineStatsResponse {

    private long worksOpened;
    private long kudosGiven;
    private long notesWritten;
    private long collectionsCreated;
    private Long totalWordsArchived;
}
