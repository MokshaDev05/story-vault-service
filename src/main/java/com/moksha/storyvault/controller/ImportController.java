package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.ImportJobRequest;
import com.moksha.storyvault.dto.ImportJobResponse;
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
}
