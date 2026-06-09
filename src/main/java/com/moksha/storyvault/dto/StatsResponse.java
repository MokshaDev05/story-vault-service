package com.moksha.storyvault.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class StatsResponse {
    private long totalStories;
    private long totalWordsArchived;
    private Map<String, Long> byStoryStatus;
    private Map<String, Long> byReadingStatus;
    private long kudosedCount;
    private long collectionsCount;
    private long connectedAccountsCount;
    private List<LabelCount> topFandoms;
    private List<LabelCount> topAuthors;
    private List<LabelCount> topRelationships;
    private List<LabelCount> topTags;
    private List<StoryAccessStat> mostAccessedStories;
    private List<StoryAccessStat> recentlyAccessedStories;
    private long storiesWithNotes;
    private long labeledStoriesCount;
    private List<LabelCount> topLabels;
}
