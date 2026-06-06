package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.DownloadRecordRequest;
import com.moksha.storyvault.dto.DownloadRecordResponse;
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
        var user = securityUtils.currentUser();
        var story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        DownloadRecord record = DownloadRecord.builder()
                .story(story)
                .source(request.getSource())
                .notes(request.getNotes())
                .build();

        return toResponse(downloadRecordRepository.save(record));
    }

    @Override
    public List<DownloadRecordResponse> getDownloads(Long storyId) {
        var user = securityUtils.currentUser();
        if (storyRepository.findByIdAndUser(storyId, user).isEmpty()) {
            throw new StoryNotFoundException(storyId);
        }
        return downloadRecordRepository.findByStoryIdOrderByDownloadedAtDesc(storyId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private DownloadRecordResponse toResponse(DownloadRecord record) {
        return DownloadRecordResponse.builder()
                .id(record.getId())
                .source(record.getSource())
                .notes(record.getNotes())
                .downloadedAt(record.getDownloadedAt())
                .build();
    }
}
