package com.moksha.storyvault.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class ImportedStoryEntry {

    private StoryRequest story;

    /** Date the user last visited this work on AO3, from the reading history page. */
    private LocalDate historyAccessDate;
}
