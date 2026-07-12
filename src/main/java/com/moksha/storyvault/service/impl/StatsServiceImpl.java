package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.AuthorStats;
import com.moksha.storyvault.dto.LabelCount;
import com.moksha.storyvault.dto.StatsResponse;
import com.moksha.storyvault.dto.StoryAccessStat;
import com.moksha.storyvault.repository.ConnectedAccountRepository;
import com.moksha.storyvault.repository.LabelRepository;
import com.moksha.storyvault.repository.ReadingHistoryRepository;
import com.moksha.storyvault.repository.ShelfRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final Pageable TOP_25  = PageRequest.of(0, 25);
    private static final Pageable TOP_200 = PageRequest.of(0, 200);

    private final StoryRepository storyRepository;
    private final ReadingHistoryRepository readingHistoryRepository;
    private final ShelfRepository shelfRepository;
    private final ConnectedAccountRepository connectedAccountRepository;
    private final LabelRepository labelRepository;
    private final SecurityUtils securityUtils;

    @Override
    public StatsResponse getStats() {
        var user = securityUtils.currentUser();

        return StatsResponse.builder()
                .totalStories(storyRepository.countByUser(user))
                .totalWordsArchived(storyRepository.sumWordCountByUser(user))
                .byStoryStatus(toStringCountMap(storyRepository.countByStoryStatusForUser(user)))
                .byReadingStatus(toStringCountMap(storyRepository.countByReadingStatusForUser(user)))
                .kudosedCount(storyRepository.countKudosedByUser(user))
                .collectionsCount(shelfRepository.countByUser(user))
                .connectedAccountsCount(connectedAccountRepository.countByUser(user))
                .topFandoms(toLabelCountList(storyRepository.topFandomsByUser(user, TOP_25)))
                .topAuthors(toLabelCountList(storyRepository.topAuthorsByUser(user, TOP_25)))
                .topRelationships(toLabelCountList(storyRepository.topRelationshipsByUser(user, TOP_25)))
                .topTags(toLabelCountList(storyRepository.topTagsByUser(user, TOP_25)))
                .mostAccessedStories(toAccessStatList(readingHistoryRepository.mostAccessedStoriesByUser(user, TOP_25)))
                .recentlyAccessedStories(toRecentList(storyRepository.recentlyAccessedByUser(user, TOP_25)))
                .topAuthorsDetailed(buildAuthorStats(storyRepository.topAuthorFandomsByUser(user, TOP_200)))
                .storiesWithNotes(storyRepository.countStoriesWithNotesByUser(user))
                .labeledStoriesCount(labelRepository.countDistinctLabeledStoriesByUser(user))
                .topLabels(toLabelCountList(labelRepository.topLabelsByUser(user)))
                .build();
    }

    private Map<String, Long> toStringCountMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                map.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        return map;
    }

    private List<LabelCount> toLabelCountList(List<Object[]> rows) {
        return rows.stream()
                .filter(r -> r[0] != null)
                .map(r -> new LabelCount(r[0].toString(), ((Number) r[1]).longValue()))
                .toList();
    }

    private List<StoryAccessStat> toAccessStatList(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new StoryAccessStat(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        ((Number) r[2]).longValue(),
                        null))
                .toList();
    }

    private List<StoryAccessStat> toRecentList(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new StoryAccessStat(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        null,
                        (LocalDateTime) r[2]))
                .toList();
    }

    private List<AuthorStats> buildAuthorStats(List<Object[]> rows) {
        // Group by author: author -> (fandom -> count)
        Map<String, Map<String, Long>> byAuthor = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String author = (String) row[0];
            String fandom = (String) row[1];
            long count    = ((Number) row[2]).longValue();
            byAuthor.computeIfAbsent(author, k -> new LinkedHashMap<>())
                    .merge(fandom, count, Long::sum);
        }
        // Build AuthorStats; total count = sum of fandom counts
        // Sort by total, take top 25
        return byAuthor.entrySet().stream()
                .map(e -> {
                    List<AuthorStats.FandomEntry> fandoms = e.getValue().entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .map(fe -> AuthorStats.FandomEntry.builder()
                                    .fandom(fe.getKey()).count(fe.getValue()).build())
                            .collect(Collectors.toList());
                    long total = fandoms.stream().mapToLong(AuthorStats.FandomEntry::getCount).sum();
                    return AuthorStats.builder().name(e.getKey()).count(total).fandoms(fandoms).build();
                })
                .sorted(Comparator.comparingLong(AuthorStats::getCount).reversed())
                .limit(25)
                .collect(Collectors.toList());
    }
}
