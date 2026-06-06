package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.FileDownloadResponse;
import com.moksha.storyvault.service.StoryFileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/stories/{storyId}/file")
@RequiredArgsConstructor
public class StoryFileController {

    private final StoryFileService storyFileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> upload(
            @PathVariable Long storyId,
            @RequestParam("file") MultipartFile file) {
        storyFileService.upload(storyId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File uploaded successfully"));
    }

    @GetMapping
    public ResponseEntity<byte[]> download(@PathVariable Long storyId) {
        FileDownloadResponse file = storyFileService.download(storyId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getFileSize())
                .body(file.getData());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long storyId) {
        storyFileService.delete(storyId);
        return ResponseEntity.ok(ApiResponse.success("File deleted successfully"));
    }
}
