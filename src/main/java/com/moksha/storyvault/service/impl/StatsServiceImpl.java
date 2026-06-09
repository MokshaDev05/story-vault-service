package com.moksha.storyvault.service.impl;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final Pageable TOP_10 = PageRequest.of(0, 10);

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
                .topFandoms(toLabelCountList(storyRepository.topFandomsByUser(user, TOP_10)))
                .topAuthors(toLabelCountList(storyRepository.topAuthorsByUser(user, TOP_10)))
                .topRelationships(toLabelCountList(storyRepository.topRelationshipsByUser(user, TOP_10)))
                .topTags(toLabelCountList(storyRepository.topTagsByUser(user, TOP_10)))
                .mostAccessedStories(toAccessStatList(readingHistoryRepository.mostAccessedStoriesByUser(user, TOP_10)))
                .recentlyAccessedStories(toRecentList(storyRepository.recentlyAccessedByUser(user, TOP_10)))
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
}
