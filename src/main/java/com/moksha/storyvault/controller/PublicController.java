package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.StoryPublicResponse;
import com.moksha.storyvault.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/stories")
@RequiredArgsConstructor
public class PublicController {

    private final StoryService storyService;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoryPublicResponse>> getPublicStory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Story found", storyService.getPublicView(id)));
    }
}
