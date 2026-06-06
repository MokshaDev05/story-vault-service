package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.NoteRequest;
import com.moksha.storyvault.dto.NoteResponse;
import com.moksha.storyvault.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stories/{storyId}/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    public ResponseEntity<ApiResponse<NoteResponse>> addNote(
            @PathVariable Long storyId,
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Note added", noteService.addNote(storyId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NoteResponse>>> getNotes(@PathVariable Long storyId) {
        return ResponseEntity.ok(ApiResponse.success("Notes retrieved", noteService.getNotes(storyId)));
    }
}
