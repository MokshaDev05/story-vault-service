package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.DownloadRecordRequest;
import com.moksha.storyvault.dto.DownloadRecordResponse;

import java.util.List;

public interface DownloadService {

    DownloadRecordResponse addDownload(Long storyId, DownloadRecordRequest request);

    List<DownloadRecordResponse> getDownloads(Long storyId);
}
