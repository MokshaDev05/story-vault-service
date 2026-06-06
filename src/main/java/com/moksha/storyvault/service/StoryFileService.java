package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.FileDownloadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface StoryFileService {

    void upload(Long storyId, MultipartFile file);

    FileDownloadResponse download(Long storyId);

    void delete(Long storyId);
}
