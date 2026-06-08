package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.ReadingHistoryStats;
import com.moksha.storyvault.dto.StoryPublicResponse;
import com.moksha.storyvault.dto.StoryRequest;
import com.moksha.storyvault.dto.StoryResponse;
import com.moksha.storyvault.dto.StorySearchRequest;
import com.moksha.storyvault.dto.UpsertResult;
import com.moksha.storyvault.exception.DuplicateStoryException;
import com.moksha.storyvault.exception.StoryNotFoundException;
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
import com.moksha.storyvault.repository.ConnectedAccountRepository;
import com.moksha.storyvault.repository.ReadingHistoryRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.repository.TagRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.StoryService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class StoryServiceImpl implements StoryService {

    private final StoryRepository storyRepository;
    private final TagRepository tagRepository;
    private final ReadingHistoryRepository readingHistoryRepository;
    private final ConnectedAccountRepository connectedAccountRepository;
    private final SecurityUtils securityUtils;

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
        return toResponse(storyRepository.save(story));
    }

    // ── Upsert ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UpsertResult upsert(StoryRequest request) {
        User user = securityUtils.currentUser();
        Optional<Story> existing = findExistingStory(request, user);

        if (existing.isPresent()) {
            log.info("Upsert — merging metadata for story id {}", existing.get().getId());
            return new UpsertResult(mergeAo3Metadata(existing.get(), request), false);
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
            return new UpsertResult(mergeAo3Metadata(concurrent, request), false);
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    public List<StoryResponse> findAll() {
        User user = securityUtils.currentUser();
        return storyRepository.findAllWithTagsByUser(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
        Story story = getOrThrow(id);
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
            User user = securityUtils.currentUser();
            connectedAccountRepository.findByIdAndUser(request.getSourceAccountId(), user)
                    .ifPresent(story::setSourceAccount);
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

        return toResponse(storyRepository.save(story));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Story story = getOrThrow(id);
        storyRepository.delete(story);
        log.info("Deleted story id: {}", id);
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
    public List<StoryResponse> advancedSearch(StorySearchRequest req) {
        User user = securityUtils.currentUser();

        // Pre-filter: story IDs that have a specific chapter accessed
        Set<Long> chapterPreFilter = null;
        if (req.getChapterAccessed() != null) {
            List<Long> ids = readingHistoryRepository.findStoryIdsWithChapterAccessed(
                    user, req.getChapterAccessed());
            if (ids.isEmpty()) return List.of();
            chapterPreFilter = new HashSet<>(ids);
        }
        final Set<Long> finalChapterFilter = chapterPreFilter;

        Specification<Story> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user"), user));

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

            if (StringUtils.hasText(req.getTagContains())) {
                query.distinct(true);
                var tagJoin = root.join("tags", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(tagJoin.get("name")),
                        "%" + req.getTagContains().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(req.getRelationshipContains())) {
                query.distinct(true);
                var relJoin = root.join("relationships", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(relJoin.as(String.class)),
                        "%" + req.getRelationshipContains().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(req.getCharacterContains())) {
                query.distinct(true);
                var charJoin = root.join("characters", JoinType.LEFT);
                predicates.add(cb.like(cb.lower(charJoin.as(String.class)),
                        "%" + req.getCharacterContains().toLowerCase() + "%"));
            }

            if (req.getCollectionId() != null) {
                query.distinct(true);
                var colJoin = root.join("collections", JoinType.INNER);
                predicates.add(cb.equal(colJoin.get("id"), req.getCollectionId()));
                predicates.add(cb.equal(colJoin.get("user"), user));
            }

            if (finalChapterFilter != null)
                predicates.add(root.get("id").in(finalChapterFilter));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Story> stories = storyRepository.findAll(spec);
        if (stories.isEmpty()) return List.of();

        List<Long> storyIds = stories.stream().map(Story::getId).collect(Collectors.toList());
        Map<Long, ReadingHistoryStats> statsMap = readingHistoryRepository.findStatsByStoryIds(storyIds)
                .stream()
                .collect(Collectors.toMap(ReadingHistoryStats::getStoryId, s -> s));

        // Post-filter: history aggregate conditions
        Stream<Story> stream = stories.stream();
        if (req.getFirstAccessedAfter() != null || req.getFirstAccessedBefore() != null
                || req.getMinAccessCount() != null) {
            stream = stream.filter(s -> {
                ReadingHistoryStats stats = statsMap.get(s.getId());
                if (req.getMinAccessCount() != null) {
                    long count = stats != null ? stats.getAccessCount() : 0L;
                    if (count < req.getMinAccessCount()) return false;
                }
                if (req.getFirstAccessedAfter() != null) {
                    if (stats == null) return false;
                    if (stats.getFirstAccessedAt().isBefore(req.getFirstAccessedAfter().atStartOfDay())) return false;
                }
                if (req.getFirstAccessedBefore() != null) {
                    if (stats == null) return false;
                    if (stats.getFirstAccessedAt().isAfter(req.getFirstAccessedBefore().atTime(23, 59, 59))) return false;
                }
                return true;
            });
        }

        StorySearchRequest.SortField sortBy = req.getSortBy() != null
                ? req.getSortBy()
                : StorySearchRequest.SortField.LAST_ACCESSED;
        boolean descending = !"asc".equalsIgnoreCase(req.getSortDir());
        Comparator<Story> comp = buildComparator(sortBy, statsMap);
        if (descending) comp = comp.reversed();

        return stream.sorted(comp)
                .map(s -> toResponse(s, statsMap.get(s.getId())))
                .collect(Collectors.toList());
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
            default -> Comparator.comparing(Story::getLastAccessedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    @Override
    public StoryPublicResponse getPublicView(Long id) {
        Story story = storyRepository.findById(id)
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

        // Tags: merge (keep user-added, add new AO3 tags)
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            story.getTags().addAll(resolveTags(request.getTags()));
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

        // Start reading on first automatic visit
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
                .readingStatus(request.getReadingStatus())
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

    private Set<Tag> resolveTags(Set<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) return new HashSet<>();
        return tagNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase())
                .map(name -> tagRepository.findByName(name)
                        .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build())))
                .collect(Collectors.toSet());
    }

    private static String normaliseUrl(String url) {
        if (!StringUtils.hasText(url)) return null;
        return url.trim().replaceFirst("[?#].*", "").replaceFirst("/+$", "");
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
                .hasFile(story.getStoryFile() != null)
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
                .build();
    }
}
