package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.DownloadFilterRequest;
import com.moksha.storyvault.dto.DownloadRecordRequest;
import com.moksha.storyvault.dto.DownloadRecordResponse;
import com.moksha.storyvault.exception.DownloadRecordNotFoundException;
import com.moksha.storyvault.exception.StoryNotFoundException;
import com.moksha.storyvault.model.DownloadRecord;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.TimelineEventType;
import com.moksha.storyvault.repository.DownloadRecordRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.DownloadService;
import com.moksha.storyvault.service.TimelineService;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class DownloadServiceImpl implements DownloadService {

    private final DownloadRecordRepository downloadRecordRepository;
    private final StoryRepository storyRepository;
    private final SecurityUtils securityUtils;
    private final TimelineService timelineService;

    @Override
    @Transactional
    public DownloadRecordResponse addDownload(Long storyId, DownloadRecordRequest request) {
        var user  = securityUtils.currentUser();
        var story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        var record = DownloadRecord.builder()
                .story(story)
                .platform(request.getPlatform())
                .fileType(request.getFileType())
                .fileName(request.getFileName())
                .storageKey(request.getStorageKey())
                .sourceUrl(request.getSourceUrl())
                .notes(request.getNotes())
                .build();

        DownloadRecordResponse response = toResponse(downloadRecordRepository.save(record));
        timelineService.record(user, story, TimelineEventType.DOWNLOAD_RECORDED,
                Map.of("storyTitle", story.getTitle(), "fandom", story.getFandom(),
                       "platform", story.getPlatform().name(),
                       "fileType", request.getFileType() != null ? request.getFileType().name() : ""));
        return response;
    }

    @Override
    public List<DownloadRecordResponse> getDownloadsForStory(Long storyId) {
        var user = securityUtils.currentUser();
        storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        return downloadRecordRepository.findByStoryIdOrderByDownloadedAtDesc(storyId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DownloadRecordResponse> getAllDownloads() {
        var user = securityUtils.currentUser();
        return downloadRecordRepository.findAllByUserOrderByDownloadedAtDesc(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DownloadRecordResponse> filterDownloads(DownloadFilterRequest req) {
        var user = securityUtils.currentUser();
        return downloadRecordRepository.findAll(buildSpec(user, req),
                        Sort.by(Sort.Direction.DESC, "downloadedAt"))
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void deleteDownload(Long storyId, Long id) {
        var user = securityUtils.currentUser();
        storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        var record = downloadRecordRepository.findByIdAndStoryId(id, storyId)
                .orElseThrow(() -> new DownloadRecordNotFoundException(id));
        downloadRecordRepository.delete(record);
    }

    private DownloadRecordResponse toResponse(DownloadRecord dr) {
        var story = dr.getStory();
        return DownloadRecordResponse.builder()
                .id(dr.getId())
                .storyId(story.getId())
                .storyTitle(story.getTitle())
                .storyFandom(story.getFandom())
                .storyAuthor(story.getAuthor())
                .storyOriginalUrl(story.getOriginalUrl())
                .platform(dr.getPlatform())
                .fileType(dr.getFileType())
                .fileName(dr.getFileName())
                .storageKey(dr.getStorageKey())
                .sourceUrl(dr.getSourceUrl())
                .notes(dr.getNotes())
                .downloadedAt(dr.getDownloadedAt())
                .build();
    }

    private Specification<DownloadRecord> buildSpec(User user, DownloadFilterRequest req) {
        return (root, query, cb) -> {
            var story = root.join("story", JoinType.INNER);
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(story.get("user"), user));
            if (req.getFileType() != null)
                predicates.add(cb.equal(root.get("fileType"), req.getFileType()));
            if (req.getPlatform() != null)
                predicates.add(cb.equal(root.get("platform"), req.getPlatform()));
            if (req.getFromDate() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("downloadedAt"),
                        req.getFromDate().atStartOfDay()));
            if (req.getToDate() != null)
                predicates.add(cb.lessThan(root.get("downloadedAt"),
                        req.getToDate().plusDays(1).atStartOfDay()));
            if (req.getFandom() != null && !req.getFandom().isBlank())
                predicates.add(cb.like(cb.lower(story.get("fandom")),
                        "%" + req.getFandom().toLowerCase() + "%"));
            if (req.getAuthor() != null && !req.getAuthor().isBlank())
                predicates.add(cb.like(cb.lower(story.get("author")),
                        "%" + req.getAuthor().toLowerCase() + "%"));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
