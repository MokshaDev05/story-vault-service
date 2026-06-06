package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.FileDownloadResponse;
import com.moksha.storyvault.exception.StoryFileAlreadyExistsException;
import com.moksha.storyvault.exception.StoryFileNotFoundException;
import com.moksha.storyvault.exception.StoryNotFoundException;
import com.moksha.storyvault.model.StoryFile;
import com.moksha.storyvault.repository.StoryFileRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.StoryFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryFileServiceImpl implements StoryFileService {

    private final StoryRepository storyRepository;
    private final StoryFileRepository storyFileRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public void upload(Long storyId, MultipartFile file) {
        var user = securityUtils.currentUser();
        var story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        if (storyFileRepository.existsByStoryId(storyId)) {
            throw new StoryFileAlreadyExistsException(storyId);
        }

        String filename = StringUtils.cleanPath(
                file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                        ? file.getOriginalFilename()
                        : "story-" + storyId + "-file"
        );

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        StoryFile storyFile = StoryFile.builder()
                .story(story)
                .filename(filename)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .fileSize(file.getSize())
                .fileData(data)
                .build();

        storyFileRepository.save(storyFile);
        log.info("Uploaded file '{}' for story id: {}", filename, storyId);
    }

    @Override
    @Transactional(readOnly = true)
    public FileDownloadResponse download(Long storyId) {
        var user = securityUtils.currentUser();
        storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        StoryFile storyFile = storyFileRepository.findByStoryId(storyId)
                .orElseThrow(() -> new StoryFileNotFoundException(storyId));

        return FileDownloadResponse.builder()
                .filename(storyFile.getFilename())
                .contentType(storyFile.getContentType())
                .fileSize(storyFile.getFileSize())
                .data(storyFile.getFileData())
                .build();
    }

    @Override
    @Transactional
    public void delete(Long storyId) {
        var user = securityUtils.currentUser();
        storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        StoryFile storyFile = storyFileRepository.findByStoryId(storyId)
                .orElseThrow(() -> new StoryFileNotFoundException(storyId));

        storyFileRepository.delete(storyFile);
        log.info("Deleted file for story id: {}", storyId);
    }
}
