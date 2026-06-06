package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.ReadingHistoryRequest;
import com.moksha.storyvault.dto.ReadingHistoryResponse;
import com.moksha.storyvault.exception.StoryNotFoundException;
import com.moksha.storyvault.model.ReadingHistory;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.repository.ReadingHistoryRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.ReadingHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReadingHistoryServiceImpl implements ReadingHistoryService {

    private final ReadingHistoryRepository readingHistoryRepository;
    private final StoryRepository storyRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public ReadingHistoryResponse log(Long storyId, ReadingHistoryRequest request) {
        User user = securityUtils.currentUser();
        Story story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        // Deduplication: skip if the same chapter page was logged within the last 5 minutes.
        // Use chapterAo3Id when available (precise); fall back to chapterNumber otherwise.
        Optional<ReadingHistory> last = readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story);
        if (last.isPresent()) {
            ReadingHistory prev = last.get();
            boolean sameChapter;
            if (request.getChapterAo3Id() != null && prev.getChapterAo3Id() != null) {
                sameChapter = Objects.equals(prev.getChapterAo3Id(), request.getChapterAo3Id());
            } else {
                sameChapter = Objects.equals(prev.getChapterNumber(), request.getChapterNumber());
            }
            boolean withinWindow = prev.getAccessedAt().isAfter(LocalDateTime.now().minusMinutes(5));
            if (sameChapter && withinWindow) {
                return toResponse(prev);
            }
        }

        ReadingHistory entry = ReadingHistory.builder()
                .story(story)
                .userId(user.getId())
                .workId(story.getSourceWorkId())
                .chapterNumber(request.getChapterNumber())
                .chapterTitle(request.getChapterTitle())
                .chapterUrl(request.getChapterUrl())
                .sourcePlatform(request.getSourcePlatform())
                .chapterAo3Id(request.getChapterAo3Id())
                .readingMode(request.getReadingMode())
                .eventType(request.getEventType() != null ? request.getEventType() : "PAGE_LOAD")
                .build();

        ReadingHistory saved = readingHistoryRepository.save(entry);

        story.setLastAccessedAt(LocalDateTime.now());
        storyRepository.save(story);

        return toResponse(saved);
    }

    @Override
    public List<ReadingHistoryResponse> listByStory(Long storyId) {
        User user = securityUtils.currentUser();
        Story story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        return readingHistoryRepository.findByStoryOrderByAccessedAtDesc(story).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ReadingHistoryResponse toResponse(ReadingHistory h) {
        return ReadingHistoryResponse.builder()
                .id(h.getId())
                .storyId(h.getStory().getId())
                .userId(h.getUserId())
                .workId(h.getWorkId())
                .accessedAt(h.getAccessedAt())
                .chapterNumber(h.getChapterNumber())
                .chapterTitle(h.getChapterTitle())
                .chapterUrl(h.getChapterUrl())
                .sourcePlatform(h.getSourcePlatform())
                .chapterAo3Id(h.getChapterAo3Id())
                .readingMode(h.getReadingMode())
                .eventType(h.getEventType())
                .build();
    }
}
