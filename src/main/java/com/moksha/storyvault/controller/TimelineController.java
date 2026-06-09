package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.TimelineEventResponse;
import com.moksha.storyvault.dto.TimelineFilterRequest;
import com.moksha.storyvault.dto.TimelineStatsResponse;
import com.moksha.storyvault.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService timelineService;

    @PostMapping
    public ResponseEntity<ApiResponse<Page<TimelineEventResponse>>> getTimeline(
            @RequestBody TimelineFilterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Timeline retrieved",
                timelineService.getTimeline(request)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<TimelineStatsResponse>> getStats(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved",
                timelineService.getStats(from, to)));
    }
}
