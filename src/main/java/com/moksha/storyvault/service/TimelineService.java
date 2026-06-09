package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.TimelineEventResponse;
import com.moksha.storyvault.dto.TimelineFilterRequest;
import com.moksha.storyvault.dto.TimelineStatsResponse;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.TimelineEventType;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.Map;

public interface TimelineService {

    /** Append a story-linked event. Safe to call within an existing transaction. */
    void record(User user, Story story, TimelineEventType eventType, Map<String, Object> metadata);

    /** Append a story-less event (e.g. IMPORT_COMPLETED). */
    void record(User user, TimelineEventType eventType, Map<String, Object> metadata);

    Page<TimelineEventResponse> getTimeline(TimelineFilterRequest request);

    TimelineStatsResponse getStats(LocalDate from, LocalDate to);
}
