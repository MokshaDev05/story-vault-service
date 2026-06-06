package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.ReadingHistoryRequest;
import com.moksha.storyvault.dto.ReadingHistoryResponse;
import com.moksha.storyvault.service.ReadingHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stories/{storyId}/access")
@RequiredArgsConstructor
public class ReadingHistoryController {

    private final ReadingHistoryService readingHistoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReadingHistoryResponse>> logAccess(
            @PathVariable Long storyId,
            @RequestBody ReadingHistoryRequest request) {
        ReadingHistoryResponse response = readingHistoryService.log(storyId, request);
        return ResponseEntity.ok(ApiResponse.success("Access logged", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReadingHistoryResponse>>> getHistory(
            @PathVariable Long storyId) {
        List<ReadingHistoryResponse> history = readingHistoryService.listByStory(storyId);
        return ResponseEntity.ok(ApiResponse.success("OK", history));
    }
}
