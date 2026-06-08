package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.DownloadRecordRequest;
import com.moksha.storyvault.dto.DownloadRecordResponse;
import com.moksha.storyvault.service.DownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DownloadController {

    private final DownloadService downloadService;

    @GetMapping("/api/v1/downloads")
    public ResponseEntity<ApiResponse<List<DownloadRecordResponse>>> getAllDownloads() {
        return ResponseEntity.ok(ApiResponse.success("Downloads retrieved", downloadService.getAllDownloads()));
    }

    @PostMapping("/api/v1/stories/{storyId}/downloads")
    public ResponseEntity<ApiResponse<DownloadRecordResponse>> addDownload(
            @PathVariable Long storyId,
            @RequestBody DownloadRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Download recorded", downloadService.addDownload(storyId, request)));
    }

    @GetMapping("/api/v1/stories/{storyId}/downloads")
    public ResponseEntity<ApiResponse<List<DownloadRecordResponse>>> getDownloadsForStory(
            @PathVariable Long storyId) {
        return ResponseEntity.ok(ApiResponse.success("Downloads retrieved",
                downloadService.getDownloadsForStory(storyId)));
    }

    @DeleteMapping("/api/v1/stories/{storyId}/downloads/{id}")
    public ResponseEntity<Void> deleteDownload(
            @PathVariable Long storyId,
            @PathVariable Long id) {
        downloadService.deleteDownload(storyId, id);
        return ResponseEntity.noContent().build();
    }
}
