package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.ImportErrorRequest;
import com.moksha.storyvault.dto.ImportHistoryBatchRequest;
import com.moksha.storyvault.dto.ImportJobRequest;
import com.moksha.storyvault.dto.ImportJobResponse;
import com.moksha.storyvault.dto.ImportProgressRequest;
import com.moksha.storyvault.service.ImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping
    public ResponseEntity<ApiResponse<ImportJobResponse>> create(
            @Valid @RequestBody ImportJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Import job created", importService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ImportJobResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success("Import jobs retrieved", importService.listAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ImportJobResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Import job retrieved", importService.getById(id)));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<ApiResponse<ImportJobResponse>> start(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Import started", importService.start(id)));
    }

    @PostMapping("/{id}/progress")
    public ResponseEntity<ApiResponse<ImportJobResponse>> updateProgress(
            @PathVariable Long id,
            @Valid @RequestBody ImportProgressRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Progress updated",
                importService.updateProgress(id, request)));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<ImportJobResponse>> pause(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Import paused", importService.pause(id)));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<ImportJobResponse>> resume(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Import resumed", importService.resume(id)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ImportJobResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Import cancelled", importService.cancel(id)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ImportJobResponse>> complete(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Import completed", importService.complete(id)));
    }

    @PostMapping("/{id}/error")
    public ResponseEntity<ApiResponse<ImportJobResponse>> error(
            @PathVariable Long id,
            @Valid @RequestBody ImportErrorRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Import failed", importService.fail(id, request)));
    }

    @PostMapping("/{id}/history-batch")
    public ResponseEntity<ApiResponse<ImportJobResponse>> historyBatch(
            @PathVariable Long id,
            @Valid @RequestBody ImportHistoryBatchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("History batch processed",
                importService.processHistoryBatch(id, request)));
    }
}
