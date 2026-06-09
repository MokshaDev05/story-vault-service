package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.LabelRequest;
import com.moksha.storyvault.dto.LabelResponse;
import com.moksha.storyvault.service.LabelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/labels")
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @PostMapping
    public ResponseEntity<ApiResponse<LabelResponse>> create(@Valid @RequestBody LabelRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Label created", labelService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LabelResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success("Labels retrieved", labelService.listAll()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<LabelResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody LabelRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Label updated", labelService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        labelService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/stories/{storyId}")
    public ResponseEntity<ApiResponse<LabelResponse>> attachStory(
            @PathVariable Long id,
            @PathVariable Long storyId) {
        return ResponseEntity.ok(ApiResponse.success("Label attached",
                labelService.attachStory(id, storyId)));
    }

    @DeleteMapping("/{id}/stories/{storyId}")
    public ResponseEntity<Void> detachStory(
            @PathVariable Long id,
            @PathVariable Long storyId) {
        labelService.detachStory(id, storyId);
        return ResponseEntity.noContent().build();
    }
}
