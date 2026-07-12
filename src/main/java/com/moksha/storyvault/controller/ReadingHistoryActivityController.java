package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.service.ReadingHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reading-history")
@RequiredArgsConstructor
public class ReadingHistoryActivityController {

    private final ReadingHistoryService readingHistoryService;

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getActivity(
            @RequestParam(defaultValue = "month") String period) {
        return ResponseEntity.ok(ApiResponse.success("OK", readingHistoryService.getActivitySummary(period)));
    }
}
