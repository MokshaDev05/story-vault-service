package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.ImportedStoryEntry;
import com.moksha.storyvault.dto.UpsertResult;
import com.moksha.storyvault.service.ReadingHistoryService;
import com.moksha.storyvault.service.StoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Processes a single imported story entry in its own transaction (REQUIRES_NEW).
 *
 * Running in a separate transaction means a DB-level failure on one entry
 * does not taint the outer import job transaction, so the job can continue,
 * increment errorCount, and persist its progress for subsequent entries.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImportEntryProcessor {

    private final StoryService storyService;
    private final ReadingHistoryService readingHistoryService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UpsertResult process(ImportedStoryEntry entry) {
        UpsertResult result = storyService.upsert(entry.getStory());
        if (entry.getHistoryAccessDate() != null) {
            LocalDateTime accessedAt = entry.getHistoryAccessDate().atTime(12, 0);
            readingHistoryService.logImported(result.story().getId(), accessedAt);
        }
        return result;
    }
}
