package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.StoryRequest;
import com.moksha.storyvault.dto.StoryResponse;
import com.moksha.storyvault.dto.StorySearchRequest;
import com.moksha.storyvault.dto.UpsertResult;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.StoryStatus;
import com.moksha.storyvault.service.StoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;

    @PostMapping
    public ResponseEntity<ApiResponse<StoryResponse>> create(@Valid @RequestBody StoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Story created", storyService.create(request)));
    }

    /**
     * Idempotent upsert for extension auto-save. Creates the story if it is new;
     * merges AO3 metadata into the existing story if it is already known.
     * Returns 201 for new stories and 200 for updates.
     */
    @PostMapping("/upsert")
    public ResponseEntity<ApiResponse<StoryResponse>> upsert(@Valid @RequestBody StoryRequest request) {
        UpsertResult result = storyService.upsert(request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        String message = result.created() ? "Story saved to vault" : "Story updated in vault";
        return ResponseEntity.status(status).body(ApiResponse.success(message, result.story()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoryResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success("Stories retrieved", storyService.findAll()));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<List<StoryResponse>>> advancedSearch(
            @RequestBody StorySearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Search results",
                storyService.advancedSearch(request)));
    }

    // /search must be declared before /{id} so Spring matches the literal first
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<StoryResponse>>> search(
            @RequestParam(required = false) String fandom,
            @RequestParam(required = false) Platform platform,
            @RequestParam(required = false) StoryStatus status,
            @RequestParam(required = false) Rating rating,
            @RequestParam(required = false) String tag) {
        return ResponseEntity.ok(ApiResponse.success("Search results",
                storyService.search(fandom, platform, status, rating, tag)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoryResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Story found", storyService.findById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StoryResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody StoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Story updated", storyService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        storyService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Story deleted"));
    }
}
