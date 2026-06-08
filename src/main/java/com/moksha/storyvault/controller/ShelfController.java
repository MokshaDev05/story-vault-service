package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.ShelfRequest;
import com.moksha.storyvault.dto.ShelfResponse;
import com.moksha.storyvault.service.ShelfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
public class ShelfController {

    private final ShelfService shelfService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShelfResponse>> create(@Valid @RequestBody ShelfRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Collection created", shelfService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ShelfResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success("Collections retrieved", shelfService.listAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShelfResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Collection retrieved", shelfService.findById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ShelfResponse>> rename(
            @PathVariable Long id,
            @Valid @RequestBody ShelfRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Collection renamed", shelfService.rename(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        shelfService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/stories/{storyId}")
    public ResponseEntity<ApiResponse<ShelfResponse>> addStory(
            @PathVariable Long id,
            @PathVariable Long storyId) {
        return ResponseEntity.ok(ApiResponse.success("Story added to collection", shelfService.addStory(id, storyId)));
    }

    @DeleteMapping("/{id}/stories/{storyId}")
    public ResponseEntity<Void> removeStory(
            @PathVariable Long id,
            @PathVariable Long storyId) {
        shelfService.removeStory(id, storyId);
        return ResponseEntity.noContent().build();
    }
}
