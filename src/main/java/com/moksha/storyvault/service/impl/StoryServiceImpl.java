package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.PagedApiResponse;
import com.moksha.storyvault.dto.PersonalNoteResponse;
import com.moksha.storyvault.dto.ReadingHistoryStats;
import com.moksha.storyvault.dto.StoryPublicResponse;
import com.moksha.storyvault.dto.StoryRequest;
import com.moksha.storyvault.dto.StoryResponse;
import com.moksha.storyvault.dto.StorySearchRequest;
import com.moksha.storyvault.dto.UpsertResult;
import com.moksha.storyvault.exception.DuplicateNoteException;
import com.moksha.storyvault.exception.DuplicateStoryException;
import com.moksha.storyvault.exception.StoryNotFoundException;
import com.moksha.storyvault.dto.LabelSummary;
import com.moksha.storyvault.dto.ShelfSummary;
import com.moksha.storyvault.model.ConnectedAccount;
import com.moksha.storyvault.model.Shelf;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.Tag;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.KudosStatus;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.ReadingStatus;
import com.moksha.storyvault.model.enums.StoryStatus;
import com.moksha.storyvault.model.enums.TimelineEventType;
import com.moksha.storyvault.repository.ConnectedAccountRepository;
import com.moksha.storyvault.repository.ReadingHistoryRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.repository.TagRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.StoryService;
import com.moksha.storyvault.service.TimelineService;
import com.moksha.storyvault.model.Label;
import com.moksha.storyvault.model.ReadingHistory;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class StoryServiceImpl implements StoryService {

    private static final java.util.regex.Pattern QUERY_FRAGMENT = java.util.regex.Pattern.compile("[?#].*");
    private static final java.util.regex.Pattern TRAILING_SLASH = java.util.regex.Pattern.compile("/+$");

    private final StoryRepository storyRepository;
    private final TagRepository tagRepository;
    private final ReadingHistoryRepository readingHistoryRepository;
    private final ConnectedAccountRepository connectedAccountRepository;
    private final SecurityUtils securityUtils;
    private final TimelineService timelineService;

    // ── Create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public StoryResponse create(StoryRequest request) {
        User user = securityUtils.currentUser();

        // Duplicate checks (strongest → weakest signal)
        if (Platform.AO3.equals(request.getPlatform()) && StringUtils.hasText(request.getSourceWorkId())) {
            storyRepository.findByPlatformAndSourceWorkIdAndUser(Platform.AO3, request.getSourceWorkId(), user)
                    .ifPresent(existing -> { throw new DuplicateStoryException(toResponse(existing)); });
        }
        String normUrl = normaliseUrl(request.getOriginalUrl());
        if (normUrl != null) {
            storyRepository.findByOriginalUrlAndUser(normUrl, user)
                    .ifPresent(existing -> { throw new DuplicateStoryException(toResponse(existing)); });
        }
        storyRepository.findByTitleIgnoreCaseAndAuthorIgnoreCaseAndUser(
                request.getTitle(), request.getAuthor(), user)
                .ifPresent(existing -> { throw new DuplicateStoryException(toResponse(existing)); });

        log.info("Creating story: {}", request.getTitle());
        Story story = buildFromRequest(request, user, normUrl);
        Story saved = storyRepository.save(story);
        timelineService.record(user, saved, TimelineEventType.STORY_FIRST_SEEN,
                storyMeta(saved));
        return toResponse(saved);
    }

    // ── Upsert ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UpsertResult upsert(StoryRequest request) {
        User user = securityUtils.currentUser();
        Optional<Story> existing = findExistingStory(request, user);

        if (existing.isPresent()) {
            log.info("Upsert — merging metadata for story id {}", existing.get().getId());
            return new UpsertResult(mergeAndRecordRevisit(existing.get(), request, user), false);
        }

        try {
            StoryResponse created = create(request);
            log.info("Upsert — new story created: {}", created.getId());
            return new UpsertResult(created, true);
        } catch (DuplicateStoryException e) {
            // Concurrent insert between our lookup and the create — merge instead
            Story concurrent = storyRepository.findById(e.getExistingStory().getId())
                    .orElseThrow(() -> e);
            log.info("Upsert — concurrent duplicate resolved for story id {}", concurrent.getId());
            return new UpsertResult(mergeAndRecordRevisit(concurrent, request, user), false);
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    public List<StoryResponse> findAll() {
        User user = securityUtils.currentUser();
        List<Story> stories = storyRepository.findAllWithTagsByUser(user);
        if (stories.isEmpty()) return List.of();
        List<Long> storyIds = stories.stream().map(Story::getId).collect(Collectors.toList());
        hydrateLabelsAndCollections(storyIds);
        Map<Long, ReadingHistoryStats> statsMap = readingHistoryRepository.findStatsByStoryIds(storyIds)
                .stream()
                .collect(Collectors.toMap(ReadingHistoryStats::getStoryId, s -> s));
        return stories.stream()
                .map(s -> toResponse(s, statsMap.get(s.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public PagedApiResponse<StoryResponse> findAllPaged(int page, int size) {
        User user = securityUtils.currentUser();
        Page<Long> idPage =
                storyRepository.findPagedIdsByUser(user, PageRequest.of(page, size));

        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return PagedApiResponse.success("Stories retrieved", List.of(),
                    idPage.getTotalElements(), idPage.getTotalPages(), page, size);
        }

        List<Story> stories = storyRepository.findByIdsWithTags(ids);
        hydrateLabelsAndCollections(ids);
        Map<Long, Integer> position = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) position.put(ids.get(i), i);
        stories.sort(Comparator.comparingInt(s -> position.getOrDefault(s.getId(), Integer.MAX_VALUE)));

        Map<Long, ReadingHistoryStats> statsMap = readingHistoryRepository.findStatsByStoryIds(ids)
                .stream()
                .collect(Collectors.toMap(ReadingHistoryStats::getStoryId, s -> s));

        List<StoryResponse> responses = stories.stream()
                .map(s -> toResponse(s, statsMap.get(s.getId())))
                .collect(Collectors.toList());

        return PagedApiResponse.success("Stories retrieved", responses,
                idPage.getTotalElements(), idPage.getTotalPages(), page, size);
    }

    @Override
    public StoryResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    // ── Update (user-initiated full update) ───────────────────────────────────

    @Override
    @Transactional
    public StoryResponse update(Long id, StoryRequest request) {
        log.info("Updating story id: {}", id);
        User user = securityUtils.currentUser();
        Story story = getOrThrow(id);

        ReadingStatus prevStatus  = story.getReadingStatus();
        Integer       prevChapter = story.getCurrentChapter();
        KudosStatus   prevKudos   = story.getKudosStatus();
        String        prevNotes   = story.getPersonalNotes();

        story.setTitle(request.getTitle());
        story.setAuthor(request.getAuthor());
        story.setFandom(request.getFandom());
        story.setPlatform(request.getPlatform());
        story.setStatus(request.getStatus() != null ? request.getStatus() : story.getStatus());
        story.setRating(request.getRating() != null ? request.getRating() : story.getRating());
        story.setSummary(request.getSummary());
        String updatedUrl = normaliseUrl(request.getOriginalUrl());
        story.setOriginalUrl(updatedUrl != null ? updatedUrl : story.getOriginalUrl());
        story.setSourceWorkId(StringUtils.hasText(request.getSourceWorkId()) ? request.getSourceWorkId() : story.getSourceWorkId());
        story.setWordCount(request.getWordCount());
        story.setChapterCount(request.getChapterCount());
        story.setTotalChapters(request.getTotalChapters());
        story.setLanguage(request.getLanguage() != null ? request.getLanguage() : story.getLanguage());
        story.setAo3PublishedDate(request.getAo3PublishedDate() != null ? request.getAo3PublishedDate() : story.getAo3PublishedDate());
        story.setAo3UpdatedDate(request.getAo3UpdatedDate() != null ? request.getAo3UpdatedDate() : story.getAo3UpdatedDate());
        story.setCompletedAt(request.getCompletedAt());
        story.setReadingStatus(request.getReadingStatus() != null ? request.getReadingStatus() : story.getReadingStatus());
        story.setCurrentChapter(request.getCurrentChapter());
        story.setCurrentChapterUrl(request.getCurrentChapterUrl());
        if (request.getKudosStatus() != null) {
            story.setKudosStatus(request.getKudosStatus());
            story.setKudosDetectedAt(LocalDateTime.now());
        }
        if (request.getSourceAccountId() != null) {
            connectedAccountRepository.findByIdAndUser(request.getSourceAccountId(), user)
                    .ifPresent(story::setSourceAccount);
        }
        if (request.getPersonalNotes() != null) {
            String trimmed = request.getPersonalNotes().strip();
            story.setPersonalNotes(trimmed.isEmpty() ? null : trimmed);
        }

        story.getTags().clear();
        story.getTags().addAll(resolveTags(request.getTags()));

        story.getRelationships().clear();
        if (request.getRelationships() != null) story.getRelationships().addAll(request.getRelationships());

        story.getCharacters().clear();
        if (request.getCharacters() != null) story.getCharacters().addAll(request.getCharacters());

        story.getArchiveWarnings().clear();
        if (request.getArchiveWarnings() != null) story.getArchiveWarnings().addAll(request.getArchiveWarnings());

        story.getCategories().clear();
        if (request.getCategories() != null) story.getCategories().addAll(request.getCategories());

        Story saved = storyRepository.save(story);
        recordUpdateEvents(user, saved, prevStatus, prevChapter, prevKudos, prevNotes, request);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Story story = getOrThrow(id);
        storyRepository.delete(story);
        log.info("Deleted story id: {}", id);
    }

    @Override
    @Transactional
    public StoryResponse updatePersonalNote(Long id, String content) {
        User user = securityUtils.currentUser();
        Story story = getOrThrow(id);
        boolean wasEmpty = !StringUtils.hasText(story.getPersonalNotes());

        String trimmed = content != null ? content.strip() : null;
        if (trimmed != null && trimmed.isEmpty()) trimmed = null;

        LocalDateTime now = LocalDateTime.now();
        if (trimmed != null) {
            if (wasEmpty) story.setPersonalNoteCreatedAt(now);
            story.setPersonalNoteUpdatedAt(now);
        } else {
            story.setPersonalNoteCreatedAt(null);
            story.setPersonalNoteUpdatedAt(null);
        }
        story.setPersonalNotes(trimmed);
        Story saved = storyRepository.save(story);

        if (StringUtils.hasText(trimmed)) {
            TimelineEventType type = wasEmpty ? TimelineEventType.NOTE_ADDED : TimelineEventType.NOTE_EDITED;
            Map<String, Object> meta = storyMeta(saved);
            meta.put("preview", trimmed.substring(0, Math.min(120, trimmed.length())));
            timelineService.record(user, saved, type, meta);
        }
        return toResponse(saved);
    }

    @Override
    public PersonalNoteResponse getPersonalNote(Long storyId) {
        Story story = getOrThrow(storyId);
        return toNoteResponse(story);
    }

    @Override
    @Transactional
    public PersonalNoteResponse createPersonalNote(Long storyId, String content) {
        Story story = getOrThrow(storyId);
        if (StringUtils.hasText(story.getPersonalNotes())) {
            throw new DuplicateNoteException(storyId);
        }
        String trimmed = content.strip();
        LocalDateTime now = LocalDateTime.now();
        story.setPersonalNotes(trimmed);
        story.setPersonalNoteCreatedAt(now);
        story.setPersonalNoteUpdatedAt(now);
        Story saved = storyRepository.save(story);
        User user = securityUtils.currentUser();
        Map<String, Object> meta = storyMeta(saved);
        meta.put("preview", trimmed.substring(0, Math.min(120, trimmed.length())));
        timelineService.record(user, saved, TimelineEventType.NOTE_ADDED, meta);
        return toNoteResponse(saved);
    }

    @Override
    @Transactional
    public void deletePersonalNote(Long storyId) {
        Story story = getOrThrow(storyId);
        story.setPersonalNotes(null);
        story.setPersonalNoteCreatedAt(null);
        story.setPersonalNoteUpdatedAt(null);
        storyRepository.save(story);
    }

    @Override
    @Transactional
    public void setLastReadDate(Long storyId, LocalDateTime at) {
        storyRepository.updateLastAccessedAt(storyId, at);
    }

    @Override
    @Transactional
    public int repairReadingStatus(boolean force) {
        User user = securityUtils.currentUser();
        Collection<ReadingStatus> toFix = List.of(ReadingStatus.STILL_READING, ReadingStatus.CAUGHT_UP);
        return storyRepository.repairReadingStatusForUser(user, toFix, force);
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Override
    public List<StoryResponse> search(String fandom, Platform platform, StoryStatus status, Rating rating, String tag) {
        User user = securityUtils.currentUser();

        Specification<Story> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user"), user));

            if (StringUtils.hasText(fandom)) {
                predicates.add(cb.like(cb.lower(root.get("fandom")), "%" + fandom.toLowerCase() + "%"));
            }
            if (platform != null) {
                predicates.add(cb.equal(root.get("platform"), platform));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (rating != null) {
                predicates.add(cb.equal(root.get("rating"), rating));
            }
            if (StringUtils.hasText(tag)) {
                query.distinct(true);
                var tagJoin = root.join("tags", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(tagJoin.get("name")), "%" + tag.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return storyRepository.findAll(spec).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PagedApiResponse<StoryResponse> advancedSearch(StorySearchRequest req, int page, int size) {
        User user = securityUtils.currentUser();

        Specification<Story> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user"), user));

            // ── Scalar field predicates ───────────────────────────────────────
            if (StringUtils.hasText(req.getTitleContains()))
                predicates.add(cb.like(cb.lower(root.get("title")),
                        "%" + req.getTitleContains().toLowerCase() + "%"));
            if (StringUtils.hasText(req.getAuthorContains()))
                predicates.add(cb.like(cb.lower(root.get("author")),
                        "%" + req.getAuthorContains().toLowerCase() + "%"));
            if (StringUtils.hasText(req.getFandomContains()))
                predicates.add(cb.like(cb.lower(root.get("fandom")),
                        "%" + req.getFandomContains().toLowerCase() + "%"));
            if (req.getPlatform() != null)
                predicates.add(cb.equal(root.get("platform"), req.getPlatform()));
            if (req.getStatus() != null)
                predicates.add(cb.equal(root.get("status"), req.getStatus()));
            if (req.getRating() != null)
                predicates.add(cb.equal(root.get("rating"), req.getRating()));
            if (req.getReadingStatus() != null)
                predicates.add(cb.equal(root.get("readingStatus"), req.getReadingStatus()));
            if (StringUtils.hasText(req.getLanguage()))
                predicates.add(cb.like(cb.lower(root.get("language")),
                        "%" + req.getLanguage().toLowerCase() + "%"));
            if (req.getMinWordCount() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("wordCount"), req.getMinWordCount()));
            if (req.getMaxWordCount() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("wordCount"), req.getMaxWordCount()));
            if (req.getMinChapters() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("chapterCount"), req.getMinChapters()));
            if (req.getMaxChapters() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("chapterCount"), req.getMaxChapters()));
            if (req.getPublishedAfter() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("ao3PublishedDate"), req.getPublishedAfter()));
            if (req.getPublishedBefore() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("ao3PublishedDate"), req.getPublishedBefore()));
            if (req.getUpdatedAfter() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("ao3UpdatedDate"), req.getUpdatedAfter()));
            if (req.getUpdatedBefore() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("ao3UpdatedDate"), req.getUpdatedBefore()));
            if (req.getLastAccessedAfter() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastAccessedAt"),
                        req.getLastAccessedAfter().atStartOfDay()));
            if (req.getLastAccessedBefore() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("lastAccessedAt"),
                        req.getLastAccessedBefore().atTime(23, 59, 59)));
            if (StringUtils.hasText(req.getNoteContains()))
                predicates.add(cb.like(cb.lower(root.get("personalNotes")),
                        "%" + req.getNoteContains().toLowerCase() + "%"));
            if (req.getHasNote() != null) {
                predicates.add(Boolean.TRUE.equals(req.getHasNote())
                        ? cb.isNotNull(root.get("personalNotes"))
                        : cb.isNull(root.get("personalNotes")));
            }

            // ── EXISTS subqueries (replace JOIN + DISTINCT) ───────────────────

            if (StringUtils.hasText(req.getTagContains())) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Story> sr = sub.correlate(root);
                Join<Story, Tag> j = sr.join("tags");
                sub.select(cb.literal(1L))
                   .where(cb.like(cb.lower(j.get("name")),
                           "%" + req.getTagContains().toLowerCase() + "%"));
                predicates.add(cb.exists(sub));
            }
            if (StringUtils.hasText(req.getRelationshipContains())) {
                Subquery<String> sub = query.subquery(String.class);
                Root<Story> sr = sub.correlate(root);
                Join<Story, String> j = sr.join("relationships");
                sub.select(j.as(String.class))
                   .where(cb.like(cb.lower(j.as(String.class)),
                           "%" + req.getRelationshipContains().toLowerCase() + "%"));
                predicates.add(cb.exists(sub));
            }
            if (StringUtils.hasText(req.getCharacterContains())) {
                Subquery<String> sub = query.subquery(String.class);
                Root<Story> sr = sub.correlate(root);
                Join<Story, String> j = sr.join("characters");
                sub.select(j.as(String.class))
                   .where(cb.like(cb.lower(j.as(String.class)),
                           "%" + req.getCharacterContains().toLowerCase() + "%"));
                predicates.add(cb.exists(sub));
            }
            if (req.getCollectionId() != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Story> sr = sub.correlate(root);
                Join<Story, Shelf> j = sr.join("collections");
                sub.select(cb.literal(1L))
                   .where(cb.and(cb.equal(j.get("id"), req.getCollectionId()),
                                 cb.equal(j.get("user"), user)));
                predicates.add(cb.exists(sub));
            }
            if (req.getLabelId() != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Story> sr = sub.correlate(root);
                Join<Story, Label> j = sr.join("labels");
                sub.select(cb.literal(1L))
                   .where(cb.and(cb.equal(j.get("id"), req.getLabelId()),
                                 cb.equal(j.get("user"), user)));
                predicates.add(cb.exists(sub));
            }
            if (req.getLabelIds() != null && !req.getLabelIds().isEmpty()) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Story> sr = sub.correlate(root);
                Join<Story, Label> j = sr.join("labels");
                sub.select(cb.literal(1L))
                   .where(cb.and(j.get("id").in(req.getLabelIds()),
                                 cb.equal(j.get("user"), user)));
                predicates.add(cb.exists(sub));
            }
            if (Boolean.TRUE.equals(req.getNoLabels())) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<Story> subStory = sub.from(Story.class);
                sub.select(cb.literal(1L));
                subStory.join("labels", JoinType.INNER);
                sub.where(cb.equal(subStory.get("id"), root.get("id")));
                predicates.add(cb.not(cb.exists(sub)));
            }

            // ── Reading history predicates (replaces pre-filter + Java post-filter) ─

            if (req.getChapterAccessed() != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<ReadingHistory> h = sub.from(ReadingHistory.class);
                sub.select(cb.literal(1L))
                   .where(cb.and(cb.equal(h.get("story"), root),
                                 cb.equal(h.get("chapterNumber"), req.getChapterAccessed())));
                predicates.add(cb.exists(sub));
            }
            if (req.getFirstAccessedAfter() != null) {
                Subquery<LocalDateTime> sub = query.subquery(LocalDateTime.class);
                Root<ReadingHistory> h = sub.from(ReadingHistory.class);
                sub.select(cb.least(h.<LocalDateTime>get("accessedAt")))
                   .where(cb.equal(h.get("story"), root));
                predicates.add(cb.greaterThanOrEqualTo(sub, req.getFirstAccessedAfter().atStartOfDay()));
            }
            if (req.getFirstAccessedBefore() != null) {
                Subquery<LocalDateTime> sub = query.subquery(LocalDateTime.class);
                Root<ReadingHistory> h = sub.from(ReadingHistory.class);
                sub.select(cb.least(h.<LocalDateTime>get("accessedAt")))
                   .where(cb.equal(h.get("story"), root));
                predicates.add(cb.lessThanOrEqualTo(sub, req.getFirstAccessedBefore().atTime(23, 59, 59)));
            }
            if (req.getMinAccessCount() != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<ReadingHistory> h = sub.from(ReadingHistory.class);
                sub.select(cb.count(h))
                   .where(cb.equal(h.get("story"), root));
                predicates.add(cb.greaterThanOrEqualTo(sub, (long) req.getMinAccessCount()));
            }
            if (req.getKudosGiven() != null) {
                predicates.add(Boolean.TRUE.equals(req.getKudosGiven())
                        ? cb.equal(root.get("kudosStatus"), KudosStatus.GIVEN)
                        : cb.notEqual(root.get("kudosStatus"), KudosStatus.GIVEN));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        StorySearchRequest.SortField sortBy = req.getSortBy() != null
                ? req.getSortBy() : StorySearchRequest.SortField.LAST_ACCESSED;
        boolean descending  = !"asc".equalsIgnoreCase(req.getSortDir());
        boolean historySort = sortBy == StorySearchRequest.SortField.FIRST_ACCESSED
                           || sortBy == StorySearchRequest.SortField.ACCESS_COUNT
                           || sortBy == StorySearchRequest.SortField.RECENTLY_READ
                           || sortBy == StorySearchRequest.SortField.LONGEST_AGO_READ
                           || sortBy == StorySearchRequest.SortField.NEVER_READ_FIRST;

        // ── Path A: DB-side pagination (9 of 11 sort fields) ─────────────────
        if (!historySort) {
            Page<Story> storyPage = storyRepository.findAll(spec,
                    PageRequest.of(page, size, buildSort(sortBy, descending)));

            List<Long> pageIds = storyPage.getContent().stream()
                    .map(Story::getId).collect(Collectors.toList());
            if (pageIds.isEmpty()) {
                return PagedApiResponse.success("Search results", List.of(),
                        storyPage.getTotalElements(), storyPage.getTotalPages(), page, size);
            }
            List<Story> hydrated = storyRepository.findByIdsWithTags(pageIds);
            hydrateLabelsAndCollections(pageIds);
            Map<Long, Integer> pos = new HashMap<>();
            for (int i = 0; i < pageIds.size(); i++) pos.put(pageIds.get(i), i);
            hydrated.sort(Comparator.comparingInt(s -> pos.getOrDefault(s.getId(), Integer.MAX_VALUE)));

            Map<Long, ReadingHistoryStats> statsMap =
                    readingHistoryRepository.findStatsByStoryIds(pageIds).stream()
                            .collect(Collectors.toMap(ReadingHistoryStats::getStoryId, s -> s));

            List<StoryResponse> responses = hydrated.stream()
                    .map(s -> toResponse(s, statsMap.get(s.getId())))
                    .collect(Collectors.toList());
            return PagedApiResponse.success("Search results", responses,
                    storyPage.getTotalElements(), storyPage.getTotalPages(), page, size);
        }

        // ── Path B: Java sort for FIRST_ACCESSED / ACCESS_COUNT ───────────────
        List<Story> stories = storyRepository.findAll(spec);
        if (stories.isEmpty()) {
            return PagedApiResponse.success("Search results", List.of(), 0, 0, page, size);
        }
        List<Long> allIds = stories.stream().map(Story::getId).collect(Collectors.toList());
        Map<Long, ReadingHistoryStats> statsMap =
                readingHistoryRepository.findStatsByStoryIds(allIds).stream()
                        .collect(Collectors.toMap(ReadingHistoryStats::getStoryId, s -> s));

        // Recency sorts encode direction in their name; sortDir is ignored for them.
        boolean fixedDirection = sortBy == StorySearchRequest.SortField.RECENTLY_READ
                              || sortBy == StorySearchRequest.SortField.LONGEST_AGO_READ
                              || sortBy == StorySearchRequest.SortField.NEVER_READ_FIRST;
        Comparator<Story> comp = buildComparator(sortBy, statsMap);
        if (descending && !fixedDirection) comp = comp.reversed();
        List<Story> sorted = stories.stream().sorted(comp).collect(Collectors.toList());

        long totalElements = sorted.size();
        int  totalPages    = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        int  fromIndex     = page * size;
        if (fromIndex >= totalElements) {
            return PagedApiResponse.success("Search results", List.of(), totalElements, totalPages, page, size);
        }
        List<Long> pageIds = sorted.subList(fromIndex, Math.min(fromIndex + size, sorted.size()))
                .stream().map(Story::getId).collect(Collectors.toList());

        List<Story> hydrated = storyRepository.findByIdsWithTags(pageIds);
        hydrateLabelsAndCollections(pageIds);
        Map<Long, Integer> pos = new HashMap<>();
        for (int i = 0; i < pageIds.size(); i++) pos.put(pageIds.get(i), i);
        hydrated.sort(Comparator.comparingInt(s -> pos.getOrDefault(s.getId(), Integer.MAX_VALUE)));

        List<StoryResponse> responses = hydrated.stream()
                .map(s -> toResponse(s, statsMap.get(s.getId())))
                .collect(Collectors.toList());
        return PagedApiResponse.success("Search results", responses, totalElements, totalPages, page, size);
    }

    private Comparator<Story> buildComparator(StorySearchRequest.SortField sortBy,
                                               Map<Long, ReadingHistoryStats> statsMap) {
        return switch (sortBy) {
            case TITLE -> Comparator.comparing(Story::getTitle,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case AUTHOR -> Comparator.comparing(Story::getAuthor,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case FANDOM -> Comparator.comparing(Story::getFandom,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case WORD_COUNT -> Comparator.comparing(Story::getWordCount,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case CHAPTER_COUNT -> Comparator.comparing(Story::getChapterCount,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case AO3_PUBLISHED_DATE -> Comparator.comparing(Story::getAo3PublishedDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case AO3_UPDATED_DATE -> Comparator.comparing(Story::getAo3UpdatedDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case CREATED_AT -> Comparator.comparing(Story::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case ACCESS_COUNT -> Comparator.comparing(
                    (Story s) -> statsMap.containsKey(s.getId()) ? statsMap.get(s.getId()).getAccessCount() : 0L,
                    Comparator.naturalOrder());
            case FIRST_ACCESSED -> Comparator.comparing(
                    (Story s) -> statsMap.containsKey(s.getId()) ? statsMap.get(s.getId()).getFirstAccessedAt() : null,
                    Comparator.<LocalDateTime>nullsLast(Comparator.naturalOrder()));
            // ── Recency sorts: direction and null placement embedded in name ──────
            case RECENTLY_READ ->
                    // DESC NULLS LAST: most recently read first, never-read at end
                    Comparator.<Story, LocalDateTime>comparing(Story::getLastAccessedAt,
                                    Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(Story::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                            .thenComparingLong(Story::getId);
            case LONGEST_AGO_READ ->
                    // ASC NULLS LAST: oldest read first, never-read at end
                    Comparator.<Story, LocalDateTime>comparing(Story::getLastAccessedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Story::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                            .thenComparingLong(Story::getId);
            case NEVER_READ_FIRST ->
                    // ASC NULLS FIRST: never-read first, then oldest read
                    Comparator.<Story, LocalDateTime>comparing(Story::getLastAccessedAt,
                                    Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(Story::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                            .thenComparingLong(Story::getId);
            default -> Comparator.comparing(Story::getLastAccessedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private Sort buildSort(StorySearchRequest.SortField sortBy, boolean descending) {
        String prop = switch (sortBy) {
            case TITLE              -> "title";
            case AUTHOR             -> "author";
            case FANDOM             -> "fandom";
            case WORD_COUNT         -> "wordCount";
            case CHAPTER_COUNT      -> "chapterCount";
            case AO3_PUBLISHED_DATE -> "ao3PublishedDate";
            case AO3_UPDATED_DATE   -> "ao3UpdatedDate";
            case CREATED_AT         -> "createdAt";
            default                 -> "lastAccessedAt";
        };
        Sort.Order order = (descending ? Sort.Order.desc(prop) : Sort.Order.asc(prop)).nullsLast();
        return Sort.by(order);
    }

    @Override
    public StoryPublicResponse getPublicView(Long id) {
        User user = securityUtils.currentUser();
        Story story = storyRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new StoryNotFoundException(id));
        return StoryPublicResponse.builder()
                .id(story.getId())
                .title(story.getTitle())
                .author(story.getAuthor())
                .fandom(story.getFandom())
                .platform(story.getPlatform())
                .status(story.getStatus())
                .rating(story.getRating())
                .summary(story.getSummary())
                .originalUrl(story.getOriginalUrl())
                .wordCount(story.getWordCount())
                .chapterCount(story.getChapterCount())
                .totalChapters(story.getTotalChapters())
                .completedAt(story.getCompletedAt())
                .tags(story.getTags().stream().map(Tag::getName).collect(Collectors.toSet()))
                .relationships(story.getRelationships() != null ? List.copyOf(story.getRelationships()) : List.of())
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Story getOrThrow(Long id) {
        User user = securityUtils.currentUser();
        return storyRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new StoryNotFoundException(id));
    }

    private Optional<Story> findExistingStory(StoryRequest request, User user) {
        if (Platform.AO3.equals(request.getPlatform()) && StringUtils.hasText(request.getSourceWorkId())) {
            Optional<Story> byWorkId = storyRepository.findByPlatformAndSourceWorkIdAndUser(
                    Platform.AO3, request.getSourceWorkId(), user);
            if (byWorkId.isPresent()) return byWorkId;
        }
        String normUrl = normaliseUrl(request.getOriginalUrl());
        if (normUrl != null) {
            Optional<Story> byUrl = storyRepository.findByOriginalUrlAndUser(normUrl, user);
            if (byUrl.isPresent()) return byUrl;
        }
        if (StringUtils.hasText(request.getTitle()) && StringUtils.hasText(request.getAuthor())) {
            return storyRepository.findByTitleIgnoreCaseAndAuthorIgnoreCaseAndUser(
                    request.getTitle(), request.getAuthor(), user);
        }
        return Optional.empty();
    }

    /** Merge AO3-scraped metadata into an existing story without touching user notes. */
    private StoryResponse mergeAo3Metadata(Story story, StoryRequest request) {
        if (StringUtils.hasText(request.getTitle()))   story.setTitle(request.getTitle());
        if (StringUtils.hasText(request.getAuthor()))  story.setAuthor(request.getAuthor());
        if (StringUtils.hasText(request.getFandom()))  story.setFandom(request.getFandom());
        if (request.getPlatform() != null)              story.setPlatform(request.getPlatform());
        if (request.getStatus()   != null)              story.setStatus(request.getStatus());
        if (request.getRating()   != null)              story.setRating(request.getRating());
        if (StringUtils.hasText(request.getSummary())) story.setSummary(request.getSummary());

        String normUrl = normaliseUrl(request.getOriginalUrl());
        if (normUrl != null) story.setOriginalUrl(normUrl);
        if (StringUtils.hasText(request.getSourceWorkId())) story.setSourceWorkId(request.getSourceWorkId());

        if (request.getWordCount()        != null) story.setWordCount(request.getWordCount());
        if (request.getChapterCount()     != null) story.setChapterCount(request.getChapterCount());
        if (request.getTotalChapters()    != null) story.setTotalChapters(request.getTotalChapters());
        if (request.getLanguage()         != null) story.setLanguage(request.getLanguage());
        if (request.getAo3PublishedDate() != null) story.setAo3PublishedDate(request.getAo3PublishedDate());
        if (request.getAo3UpdatedDate()   != null) story.setAo3UpdatedDate(request.getAo3UpdatedDate());

        // Backfill completedAt from AO3 updated date when work transitions to COMPLETE
        if (StoryStatus.COMPLETE.equals(request.getStatus())
                && story.getCompletedAt() == null
                && request.getAo3UpdatedDate() != null) {
            story.setCompletedAt(request.getAo3UpdatedDate());
        }

        // Tags: merge (keep user-added, add new AO3 tags).
        // Short-circuit: skip the batch SELECT entirely if all incoming tags are already
        // present, which is the common case on re-imports.
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            Set<String> incomingNorm = request.getTags().stream()
                    .filter(n -> n != null && !n.isBlank())
                    .map(n -> n.trim().toLowerCase())
                    .collect(Collectors.toSet());
            Set<String> existingNames = story.getTags().stream()
                    .map(Tag::getName)
                    .collect(Collectors.toSet());
            incomingNorm.removeAll(existingNames);
            if (!incomingNorm.isEmpty()) {
                story.getTags().addAll(resolveTags(incomingNorm));
            }
        }

        // AO3 canonical lists: replace wholesale
        if (request.getRelationships() != null) {
            story.getRelationships().clear();
            story.getRelationships().addAll(request.getRelationships());
        }
        if (request.getCharacters() != null) {
            story.getCharacters().clear();
            story.getCharacters().addAll(request.getCharacters());
        }
        if (request.getArchiveWarnings() != null) {
            story.getArchiveWarnings().clear();
            story.getArchiveWarnings().addAll(request.getArchiveWarnings());
        }
        if (request.getCategories() != null) {
            story.getCategories().clear();
            story.getCategories().addAll(request.getCategories());
        }

        advanceReadingProgress(story, request);
        mergeKudosStatus(story, request);
        story.setLastAccessedAt(LocalDateTime.now());
        return toResponse(storyRepository.save(story));
    }

    /**
     * Update kudos status on auto-upsert.
     * GIVEN always wins (kudos cannot be revoked on AO3).
     * Any status updates from UNKNOWN; NOT_DETECTED does not overwrite NOT_DETECTED.
     * Incoming UNKNOWN is ignored (no detection signal).
     */
    private void mergeKudosStatus(Story story, StoryRequest request) {
        if (request.getKudosStatus() == null || request.getKudosStatus() == KudosStatus.UNKNOWN) return;
        KudosStatus current = story.getKudosStatus() != null ? story.getKudosStatus() : KudosStatus.UNKNOWN;
        if (request.getKudosStatus() == KudosStatus.GIVEN || current == KudosStatus.UNKNOWN) {
            story.setKudosStatus(request.getKudosStatus());
            story.setKudosDetectedAt(LocalDateTime.now());
        }
    }

    /** Auto-advance reading status and chapter; never goes backwards (except REREADING). */
    private void advanceReadingProgress(Story story, StoryRequest request) {
        ReadingStatus current = story.getReadingStatus();

        // Never override deliberate user choices
        if (current == ReadingStatus.FINISHED_READING
                || current == ReadingStatus.DNF
                || current == ReadingStatus.ON_HOLD) {
            return;
        }

        // If the work is confirmed complete (via import metadata or chapter ratio), finish it.
        // REREADING is intentional — preserve it even for complete works.
        StoryStatus effectiveStatus = request.getStatus() != null ? request.getStatus() : story.getStatus();
        if (StoryStatus.COMPLETE.equals(effectiveStatus) && current != ReadingStatus.REREADING) {
            story.setReadingStatus(ReadingStatus.FINISHED_READING);
            return;
        }

        // Start reading on first automatic visit (only for incomplete/unknown works)
        if (current == null || current == ReadingStatus.WANT_TO_READ) {
            story.setReadingStatus(ReadingStatus.STILL_READING);
        }

        // Advance current chapter (only forward, except when rereading)
        if (request.getCurrentChapter() != null) {
            boolean advance = story.getCurrentChapter() == null
                    || current == ReadingStatus.REREADING
                    || request.getCurrentChapter() > story.getCurrentChapter();
            if (advance) {
                story.setCurrentChapter(request.getCurrentChapter());
                if (StringUtils.hasText(request.getCurrentChapterUrl())) {
                    story.setCurrentChapterUrl(request.getCurrentChapterUrl());
                }
            }
        }

        // Detect CAUGHT_UP: user's current chapter >= published chapter count
        Integer published = request.getChapterCount();
        Integer current_ch = story.getCurrentChapter();
        if (published != null && current_ch != null && current_ch >= published) {
            if (StoryStatus.COMPLETE.equals(story.getStatus())) {
                story.setReadingStatus(ReadingStatus.FINISHED_READING);
            } else {
                story.setReadingStatus(ReadingStatus.CAUGHT_UP);
            }
        }
    }

    private Story buildFromRequest(StoryRequest request, User user, String normUrl) {
        ConnectedAccount sourceAccount = null;
        if (request.getSourceAccountId() != null) {
            sourceAccount = connectedAccountRepository.findByIdAndUser(request.getSourceAccountId(), user).orElse(null);
        }

        Story story = Story.builder()
                .title(request.getTitle())
                .author(request.getAuthor())
                .fandom(request.getFandom())
                .platform(request.getPlatform())
                .status(request.getStatus() != null ? request.getStatus() : StoryStatus.ONGOING)
                .rating(request.getRating() != null ? request.getRating() : Rating.NOT_RATED)
                .summary(request.getSummary())
                .originalUrl(normUrl)
                .sourceWorkId(request.getSourceWorkId())
                .wordCount(request.getWordCount())
                .chapterCount(request.getChapterCount())
                .totalChapters(request.getTotalChapters())
                .ao3PublishedDate(request.getAo3PublishedDate())
                .ao3UpdatedDate(request.getAo3UpdatedDate())
                .language(request.getLanguage())
                .completedAt(request.getCompletedAt())
                .readingStatus(
                    request.getReadingStatus() != null ? request.getReadingStatus() :
                    StoryStatus.COMPLETE.equals(request.getStatus()) ? ReadingStatus.FINISHED_READING :
                    null
                )
                .currentChapter(request.getCurrentChapter())
                .currentChapterUrl(request.getCurrentChapterUrl())
                .kudosStatus(request.getKudosStatus() != null ? request.getKudosStatus() : KudosStatus.UNKNOWN)
                .sourceAccount(sourceAccount)
                .user(user)
                .build();

        // Backfill completedAt for COMPLETE works when no explicit date provided
        if (StoryStatus.COMPLETE.equals(story.getStatus())
                && story.getCompletedAt() == null
                && request.getAo3UpdatedDate() != null) {
            story.setCompletedAt(request.getAo3UpdatedDate());
        }

        story.setTags(resolveTags(request.getTags()));
        story.setRelationships(request.getRelationships() != null ? new ArrayList<>(request.getRelationships()) : new ArrayList<>());
        story.setCharacters(request.getCharacters() != null ? new ArrayList<>(request.getCharacters()) : new ArrayList<>());
        story.setArchiveWarnings(request.getArchiveWarnings() != null ? new ArrayList<>(request.getArchiveWarnings()) : new ArrayList<>());
        story.setCategories(request.getCategories() != null ? new ArrayList<>(request.getCategories()) : new ArrayList<>());
        return story;
    }

    private void hydrateLabelsAndCollections(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        storyRepository.findByIdsWithLabels(ids);
        storyRepository.findByIdsWithCollections(ids);
    }

    private Set<Tag> resolveTags(Set<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return new HashSet<>();
        Set<String> normalized = tagNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase())
                .collect(Collectors.toSet());
        if (normalized.isEmpty()) return new HashSet<>();
        Map<String, Tag> existing = tagRepository.findAllByNameIn(normalized).stream()
                .collect(Collectors.toMap(Tag::getName, t -> t));
        Set<Tag> result = new HashSet<>(existing.values());
        for (String name : normalized) {
            if (!existing.containsKey(name)) {
                result.add(tagRepository.save(Tag.builder().name(name).build()));
            }
        }
        return result;
    }

    /** Merge AO3 metadata and record STORY_REVISITED + any derived change events. */
    private StoryResponse mergeAndRecordRevisit(Story story, StoryRequest request, User user) {
        ReadingStatus prevStatus  = story.getReadingStatus();
        Integer       prevChapter = story.getCurrentChapter();
        KudosStatus   prevKudos   = story.getKudosStatus();

        StoryResponse response = mergeAo3Metadata(story, request);

        timelineService.record(user, story, TimelineEventType.STORY_REVISITED, storyMeta(story));

        if (!Objects.equals(story.getCurrentChapter(), prevChapter) && story.getCurrentChapter() != null) {
            Map<String, Object> meta = storyMeta(story);
            meta.put("from", prevChapter != null ? prevChapter : 0);
            meta.put("to", story.getCurrentChapter());
            timelineService.record(user, story, TimelineEventType.CHAPTER_PROGRESS_UPDATED, meta);
        }
        if (story.getReadingStatus() != null && story.getReadingStatus() != prevStatus) {
            Map<String, Object> meta = storyMeta(story);
            meta.put("from", prevStatus != null ? prevStatus.name() : "NONE");
            meta.put("to", story.getReadingStatus().name());
            timelineService.record(user, story, TimelineEventType.READING_STATUS_CHANGED, meta);
        }
        if (story.getKudosStatus() == KudosStatus.GIVEN && prevKudos != KudosStatus.GIVEN) {
            timelineService.record(user, story, TimelineEventType.KUDOS_GIVEN, storyMeta(story));
        }

        return response;
    }

    /** Emit change events for a user-initiated update(). */
    private void recordUpdateEvents(User user, Story saved,
                                    ReadingStatus prevStatus, Integer prevChapter, KudosStatus prevKudos,
                                    String prevNotes, StoryRequest request) {
        if (!Objects.equals(saved.getCurrentChapter(), prevChapter) && saved.getCurrentChapter() != null) {
            Map<String, Object> meta = storyMeta(saved);
            meta.put("from", prevChapter != null ? prevChapter : 0);
            meta.put("to", saved.getCurrentChapter());
            timelineService.record(user, saved, TimelineEventType.CHAPTER_PROGRESS_UPDATED, meta);
        }
        if (saved.getReadingStatus() != null && saved.getReadingStatus() != prevStatus) {
            Map<String, Object> meta = storyMeta(saved);
            meta.put("from", prevStatus != null ? prevStatus.name() : "NONE");
            meta.put("to", saved.getReadingStatus().name());
            timelineService.record(user, saved, TimelineEventType.READING_STATUS_CHANGED, meta);
        }
        if (saved.getKudosStatus() == KudosStatus.GIVEN && prevKudos != KudosStatus.GIVEN) {
            timelineService.record(user, saved, TimelineEventType.KUDOS_GIVEN, storyMeta(saved));
        }
        if (request.getPersonalNotes() != null && StringUtils.hasText(saved.getPersonalNotes())) {
            TimelineEventType type = !StringUtils.hasText(prevNotes) ? TimelineEventType.NOTE_ADDED : TimelineEventType.NOTE_EDITED;
            Map<String, Object> meta = storyMeta(saved);
            String note = saved.getPersonalNotes();
            meta.put("preview", note.substring(0, Math.min(120, note.length())));
            timelineService.record(user, saved, type, meta);
        }
    }

    /** Base metadata map included in every story-linked event. */
    private Map<String, Object> storyMeta(Story story) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("storyTitle", story.getTitle());
        meta.put("fandom", story.getFandom());
        meta.put("platform", story.getPlatform().name());
        return meta;
    }

    private static String normaliseUrl(String url) {
        if (!StringUtils.hasText(url)) return null;
        String stripped = url.trim();
        stripped = QUERY_FRAGMENT.matcher(stripped).replaceFirst("");
        return TRAILING_SLASH.matcher(stripped).replaceFirst("");
    }

    private PersonalNoteResponse toNoteResponse(Story story) {
        return PersonalNoteResponse.builder()
                .storyId(story.getId())
                .content(story.getPersonalNotes())
                .hasNote(StringUtils.hasText(story.getPersonalNotes()))
                .createdAt(story.getPersonalNoteCreatedAt())
                .updatedAt(story.getPersonalNoteUpdatedAt())
                .build();
    }

    private StoryResponse toResponse(Story story) {
        return toResponse(story, null);
    }

    private StoryResponse toResponse(Story story, ReadingHistoryStats stats) {
        return StoryResponse.builder()
                .id(story.getId())
                .title(story.getTitle())
                .author(story.getAuthor())
                .fandom(story.getFandom())
                .platform(story.getPlatform())
                .status(story.getStatus())
                .rating(story.getRating())
                .summary(story.getSummary())
                .originalUrl(story.getOriginalUrl())
                .sourceWorkId(story.getSourceWorkId())
                .wordCount(story.getWordCount())
                .chapterCount(story.getChapterCount())
                .totalChapters(story.getTotalChapters())
                .ao3PublishedDate(story.getAo3PublishedDate())
                .ao3UpdatedDate(story.getAo3UpdatedDate())
                .language(story.getLanguage())
                .completedAt(story.getCompletedAt())
                .createdAt(story.getCreatedAt())
                .updatedAt(story.getUpdatedAt())
                .tags(story.getTags().stream().map(Tag::getName).collect(Collectors.toSet()))
                .relationships(story.getRelationships() != null ? List.copyOf(story.getRelationships()) : List.of())
                .characters(story.getCharacters() != null ? List.copyOf(story.getCharacters()) : List.of())
                .archiveWarnings(story.getArchiveWarnings() != null ? List.copyOf(story.getArchiveWarnings()) : List.of())
                .categories(story.getCategories() != null ? List.copyOf(story.getCategories()) : List.of())
                .hasFile(story.isHasFile())
                .readingStatus(story.getReadingStatus())
                .currentChapter(story.getCurrentChapter())
                .currentChapterUrl(story.getCurrentChapterUrl())
                .lastAccessedAt(story.getLastAccessedAt())
                .firstAccessedAt(stats != null ? stats.getFirstAccessedAt() : null)
                .accessCount(stats != null ? stats.getAccessCount() : null)
                .kudosStatus(story.getKudosStatus())
                .kudosDetectedAt(story.getKudosDetectedAt())
                .sourceAccountId(story.getSourceAccount() != null ? story.getSourceAccount().getId() : null)
                .collections(story.getCollections().stream()
                        .map(c -> ShelfSummary.builder().id(c.getId()).name(c.getName()).build())
                        .sorted(java.util.Comparator.comparing(ShelfSummary::getName))
                        .collect(Collectors.toList()))
                .personalNotes(story.getPersonalNotes())
                .hasNote(StringUtils.hasText(story.getPersonalNotes()))
                .notePreview(story.getPersonalNotes() != null
                        ? story.getPersonalNotes().substring(0, Math.min(120, story.getPersonalNotes().length()))
                        : null)
                .labels(story.getLabels().stream()
                        .map(l -> LabelSummary.builder().id(l.getId()).name(l.getName()).color(l.getColor()).build())
                        .sorted(java.util.Comparator.comparing(LabelSummary::getName))
                        .collect(Collectors.toList()))
                .build();
    }
}
