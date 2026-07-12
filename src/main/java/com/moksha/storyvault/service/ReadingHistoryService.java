package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.ReadingHistoryRequest;
import com.moksha.storyvault.dto.ReadingHistoryResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ReadingHistoryService {

    ReadingHistoryResponse log(Long storyId, ReadingHistoryRequest request);

    List<ReadingHistoryResponse> listByStory(Long storyId);

    /** Create a historical reading history entry with a back-filled access date.
     *  Deduplicates: skips if an AO3_IMPORT entry already exists for this story on the same calendar day. */
    ReadingHistoryResponse logImported(Long storyId, LocalDateTime accessedAt);

    /** Returns a map of period buckets (date strings) to distinct work counts. */
    Map<String, Long> getActivitySummary(String period);
}
