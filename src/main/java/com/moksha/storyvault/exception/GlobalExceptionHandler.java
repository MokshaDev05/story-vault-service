package com.moksha.storyvault.exception;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.StoryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthFailure(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid username or password"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler(DuplicateStoryException.class)
    public ResponseEntity<ApiResponse<StoryResponse>> handleDuplicateStory(DuplicateStoryException ex) {
        log.info("Duplicate story detected: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.<StoryResponse>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .data(ex.getExistingStory())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(StoryNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleStoryNotFound(StoryNotFoundException ex) {
        log.warn("Story not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(LabelNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleLabelNotFound(LabelNotFoundException ex) {
        log.warn("Label not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateLabelException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateLabel(DuplicateLabelException ex) {
        log.info("Duplicate label: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateNoteException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateNote(DuplicateNoteException ex) {
        log.info("Duplicate note: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DownloadRecordNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDownloadNotFound(DownloadRecordNotFoundException ex) {
        log.warn("Download record not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ShelfNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleShelfNotFound(ShelfNotFoundException ex) {
        log.warn("Collection not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ImportJobNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleImportJobNotFound(ImportJobNotFoundException ex) {
        log.warn("Import job not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalImportStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalImportState(IllegalImportStateException ex) {
        log.warn("Illegal import state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ConnectedAccountNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountNotFound(ConnectedAccountNotFoundException ex) {
        log.warn("Connected account not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(StoryFileNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileNotFound(StoryFileNotFoundException ex) {
        log.warn("Story file not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(StoryFileAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileAlreadyExists(StoryFileAlreadyExistsException ex) {
        log.warn("Story file already exists: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error("File size exceeds the maximum allowed limit of 50MB"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            errors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}
