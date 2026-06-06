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
@RequestMapping("/api/v1/stories/{storyId}/downloads")
@RequiredArgsConstructor
public class DownloadController {

    private final DownloadService downloadService;

    @PostMapping
    public ResponseEntity<ApiResponse<DownloadRecordResponse>> addDownload(
            @PathVariable Long storyId,
            @RequestBody DownloadRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Download recorded", downloadService.addDownload(storyId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DownloadRecordResponse>>> getDownloads(@PathVariable Long storyId) {
        return ResponseEntity.ok(ApiResponse.success("Download history retrieved",
                downloadService.getDownloads(storyId)));
    }
}
