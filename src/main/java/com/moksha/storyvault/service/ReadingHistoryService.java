package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.ReadingHistoryRequest;
import com.moksha.storyvault.dto.ReadingHistoryResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface ReadingHistoryService {

    ReadingHistoryResponse log(Long storyId, ReadingHistoryRequest request);

    List<ReadingHistoryResponse> listByStory(Long storyId);

    /** Create a historical reading history entry with a back-filled access date.
     *  Deduplicates: skips if an AO3_IMPORT entry already exists for this story on the same calendar day. */
    ReadingHistoryResponse logImported(Long storyId, LocalDateTime accessedAt);
}
