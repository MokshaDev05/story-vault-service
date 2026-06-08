package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.DownloadRecordRequest;
import com.moksha.storyvault.dto.DownloadRecordResponse;

import java.util.List;

public interface DownloadService {

    DownloadRecordResponse addDownload(Long storyId, DownloadRecordRequest request);

    List<DownloadRecordResponse> getDownloadsForStory(Long storyId);

    List<DownloadRecordResponse> getAllDownloads();

    void deleteDownload(Long storyId, Long id);
}
