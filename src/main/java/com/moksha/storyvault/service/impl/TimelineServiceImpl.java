package com.moksha.storyvault.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moksha.storyvault.dto.TimelineEventResponse;
import com.moksha.storyvault.dto.TimelineFilterRequest;
import com.moksha.storyvault.dto.TimelineStatsResponse;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.TimelineEvent;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.TimelineEventType;
import com.moksha.storyvault.repository.TimelineEventRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.TimelineService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TimelineServiceImpl implements TimelineService {

    private final TimelineEventRepository timelineEventRepository;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;

    // ── Event recording ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void record(User user, Story story, TimelineEventType eventType, Map<String, Object> metadata) {
        timelineEventRepository.save(TimelineEvent.builder()
                .user(user)
                .story(story)
                .eventType(eventType)
                .eventTimestamp(LocalDateTime.now())
                .metadata(toJson(metadata))
                .build());
    }

    @Override
    @Transactional
    public void record(User user, TimelineEventType eventType, Map<String, Object> metadata) {
        timelineEventRepository.save(TimelineEvent.builder()
                .user(user)
                .eventType(eventType)
                .eventTimestamp(LocalDateTime.now())
                .metadata(toJson(metadata))
                .build());
    }

    // ── Timeline query ────────────────────────────────────────────────────────

    @Override
    public Page<TimelineEventResponse> getTimeline(TimelineFilterRequest req) {
        User user = securityUtils.currentUser();
        Pageable pageable = PageRequest.of(req.getPage(), req.getSize(),
                Sort.by(Sort.Direction.DESC, "eventTimestamp"));

        List<TimelineEventType> types = parseEventTypes(req.getEventTypes());
        LocalDateTime fromDt = req.getFromDate() != null ? req.getFromDate().atStartOfDay() : null;
        LocalDateTime toDt   = req.getToDate()   != null ? req.getToDate().atTime(23, 59, 59) : null;

        Specification<TimelineEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user"), user));

            if (fromDt != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("eventTimestamp"), fromDt));
            if (toDt != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("eventTimestamp"), toDt));
            if (types != null && !types.isEmpty())
                predicates.add(root.get("eventType").in(types));

            if (StringUtils.hasText(req.getFandom())) {
                var storyJoin = root.join("story", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(storyJoin.get("fandom")),
                        "%" + req.getFandom().toLowerCase() + "%"));
            }
            if (req.getCollectionId() != null) {
                var storyJoin = root.join("story", JoinType.INNER);
                var collJoin  = storyJoin.join("collections", JoinType.INNER);
                predicates.add(cb.equal(collJoin.get("id"), req.getCollectionId()));
                predicates.add(cb.equal(collJoin.get("user"), user));
            }
            if (req.getLabelId() != null) {
                var storyJoin = root.join("story", JoinType.INNER);
                var labelJoin = storyJoin.join("labels", JoinType.INNER);
                predicates.add(cb.equal(labelJoin.get("id"), req.getLabelId()));
                predicates.add(cb.equal(labelJoin.get("user"), user));
            }
            if (StringUtils.hasText(req.getSearch())) {
                predicates.add(cb.like(cb.lower(root.get("metadata")),
                        "%" + req.getSearch().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return timelineEventRepository.findAll(spec, pageable).map(this::toResponse);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Override
    public TimelineStatsResponse getStats(LocalDate from, LocalDate to) {
        User user   = securityUtils.currentUser();
        LocalDateTime fromDt = from != null ? from.atStartOfDay()         : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime toDt   = to   != null ? to.atTime(23, 59, 59)       : LocalDateTime.now();

        long worksOpened = timelineEventRepository.countByUserTypeAndRange(
                user, TimelineEventType.STORY_FIRST_SEEN, fromDt, toDt);
        long kudosGiven = timelineEventRepository.countByUserTypeAndRange(
                user, TimelineEventType.KUDOS_GIVEN, fromDt, toDt);
        long notesWritten = timelineEventRepository.countByUserTypesAndRange(
                user, List.of(TimelineEventType.NOTE_ADDED, TimelineEventType.NOTE_EDITED), fromDt, toDt);
        long collectionsCreated = timelineEventRepository.countByUserTypeAndRange(
                user, TimelineEventType.COLLECTION_ADDED, fromDt, toDt);
        Long totalWords = timelineEventRepository.sumWordsForFirstSeenInRange(user, fromDt, toDt);

        return TimelineStatsResponse.builder()
                .worksOpened(worksOpened)
                .kudosGiven(kudosGiven)
                .notesWritten(notesWritten)
                .collectionsCreated(collectionsCreated)
                .totalWordsArchived(totalWords)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TimelineEventResponse toResponse(TimelineEvent e) {
        Story story = e.getStory();
        return TimelineEventResponse.builder()
                .id(e.getId())
                .eventType(e.getEventType().name())
                .eventTimestamp(e.getEventTimestamp())
                .storyId(story != null ? story.getId() : null)
                .storyTitle(story != null ? story.getTitle() : null)
                .storyFandom(story != null ? story.getFandom() : null)
                .metadata(e.getMetadata())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private List<TimelineEventType> parseEventTypes(List<String> names) {
        if (names == null || names.isEmpty()) return null;
        return names.stream()
                .map(s -> {
                    try { return TimelineEventType.valueOf(s); }
                    catch (IllegalArgumentException ignored) { return null; }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private String toJson(Map<String, Object> meta) {
        if (meta == null || meta.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
