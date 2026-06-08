package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.DownloadRecordRequest;
import com.moksha.storyvault.dto.DownloadRecordResponse;
import com.moksha.storyvault.exception.DownloadRecordNotFoundException;
import com.moksha.storyvault.exception.StoryNotFoundException;
import com.moksha.storyvault.model.DownloadRecord;
import com.moksha.storyvault.repository.DownloadRecordRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.DownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class DownloadServiceImpl implements DownloadService {

    private final DownloadRecordRepository downloadRecordRepository;
    private final StoryRepository storyRepository;
    private final SecurityUtils securityUtils;

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

        return toResponse(downloadRecordRepository.save(record));
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
        return DownloadRecordResponse.builder()
                .id(dr.getId())
                .storyId(dr.getStory().getId())
                .storyTitle(dr.getStory().getTitle())
                .platform(dr.getPlatform())
                .fileType(dr.getFileType())
                .fileName(dr.getFileName())
                .storageKey(dr.getStorageKey())
                .sourceUrl(dr.getSourceUrl())
                .notes(dr.getNotes())
                .downloadedAt(dr.getDownloadedAt())
                .build();
    }
}
