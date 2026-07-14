package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.ImportErrorRequest;
import com.moksha.storyvault.dto.ImportHistoryBatchRequest;
import com.moksha.storyvault.dto.ImportJobRequest;
import com.moksha.storyvault.dto.ImportJobResponse;
import com.moksha.storyvault.dto.ImportProgressRequest;
import com.moksha.storyvault.exception.IllegalImportStateException;
import com.moksha.storyvault.exception.ImportJobNotFoundException;
import com.moksha.storyvault.model.ImportJob;
import com.moksha.storyvault.model.enums.ImportStatus;
import com.moksha.storyvault.repository.ImportJobRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.ImportService;
import com.moksha.storyvault.service.ReadingHistoryService;
import com.moksha.storyvault.service.StoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ImportServiceImpl implements ImportService {

    private final ImportJobRepository importJobRepository;
    private final SecurityUtils securityUtils;
    private final StoryService storyService;
    private final ReadingHistoryService readingHistoryService;
    private final ImportEntryProcessor entryProcessor;

    @Override
    @Transactional
    public ImportJobResponse create(ImportJobRequest request) {
        var user = securityUtils.currentUser();
        var job = ImportJob.builder()
                .user(user)
                .platform(request.getPlatform())
                .importType(request.getImportType())
                .ao3Username(request.getAo3Username())
                .build();
        return toResponse(importJobRepository.save(job));
    }

    @Override
    public ImportJobResponse getById(Long id) {
        var user = securityUtils.currentUser();
        return toResponse(requireOwned(id, user.getId()));
    }

    @Override
    public List<ImportJobResponse> listAll() {
        var user = securityUtils.currentUser();
        return importJobRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ImportJobResponse start(Long id) {
        var user = securityUtils.currentUser();
        var job = requireOwned(id, user.getId());
        requireStatus(job, ImportStatus.PENDING);
        job.setStatus(ImportStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        return toResponse(importJobRepository.save(job));
    }

    @Override
    @Transactional
    public ImportJobResponse updateProgress(Long id, ImportProgressRequest req) {
        var user = securityUtils.currentUser();
        var job = requireOwned(id, user.getId());
        requireStatus(job, ImportStatus.RUNNING);

        job.setCurrentPage(req.getCurrentPage());
        if (req.getTotalPages() != null) job.setTotalPages(req.getTotalPages());
        job.setItemsProcessed(req.getItemsProcessed());
        importJobRepository.save(job);

        if (req.getStories() != null) {
            for (var story : req.getStories()) {
                if (story == null || story.getPlatform() == null || story.getTitle() == null) continue;
                try {
                    storyService.upsert(story);
                } catch (Exception e) {
                    log.warn("Import job {} skipped story due to error: {}", id, e.getMessage());
                }
            }
        }

        return toResponse(importJobRepository.findById(id).orElse(job));
    }

    @Override
    @Transactional
    public ImportJobResponse pause(Long id) {
        var user = securityUtils.currentUser();
        var job = requireOwned(id, user.getId());
        requireStatus(job, ImportStatus.RUNNING);
        job.setStatus(ImportStatus.PAUSED);
        return toResponse(importJobRepository.save(job));
    }

    @Override
    @Transactional
    public ImportJobResponse resume(Long id) {
        var user = securityUtils.currentUser();
        var job = requireOwned(id, user.getId());
        requireStatus(job, ImportStatus.PAUSED);
        job.setStatus(ImportStatus.RUNNING);
        return toResponse(importJobRepository.save(job));
    }

    @Override
    @Transactional
    public ImportJobResponse cancel(Long id) {
        var user = securityUtils.currentUser();
        var job = requireOwned(id, user.getId());
        if (job.getStatus() == ImportStatus.COMPLETED || job.getStatus() == ImportStatus.FAILED) {
            throw new IllegalImportStateException(
                    "Cannot cancel a job that is already " + job.getStatus());
        }
        job.setStatus(ImportStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        return toResponse(importJobRepository.save(job));
    }

    @Override
    @Transactional
    public ImportJobResponse complete(Long id) {
        var user = securityUtils.currentUser();
        var job = requireOwned(id, user.getId());
        requireStatus(job, ImportStatus.RUNNING);
        job.setStatus(ImportStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        return toResponse(importJobRepository.save(job));
    }

    @Override
    @Transactional
    public ImportJobResponse fail(Long id, ImportErrorRequest req) {
        var user = securityUtils.currentUser();
        var job = requireOwned(id, user.getId());
        requireStatus(job, ImportStatus.RUNNING);
        job.setStatus(ImportStatus.FAILED);
        job.setCompletedAt(LocalDateTime.now());
        job.setErrorCount(job.getErrorCount() + 1);
        job.setErrorMessage(req.getErrorMessage());
        job.setLastError(req.getErrorMessage());
        return toResponse(importJobRepository.save(job));
    }

    @Override
    @Transactional
    public ImportJobResponse processHistoryBatch(Long id, ImportHistoryBatchRequest req) {
        var user = securityUtils.currentUser();
        var job  = requireOwned(id, user.getId());
        requireStatus(job, ImportStatus.RUNNING);

        int processedInBatch = 0;
        for (var entry : req.getEntries()) {
            if (entry.getStory() == null) continue;
            try {
                entryProcessor.process(entry);
                processedInBatch++;
            } catch (Exception e) {
                log.warn("Import job {} skipped story '{}': {}",
                        id, entry.getStory().getTitle(), e.getMessage());
                job.setErrorCount(job.getErrorCount() + 1);
                job.setLastError(e.getMessage());
            }
        }

        job.setCurrentPage(req.getCurrentPage());
        if (req.getTotalPages() != null) job.setTotalPages(req.getTotalPages());
        job.setItemsProcessed(job.getItemsProcessed() + processedInBatch);

        return toResponse(importJobRepository.save(job));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ImportJob requireOwned(Long id, Long userId) {
        var job = importJobRepository.findById(id)
                .orElseThrow(() -> new ImportJobNotFoundException(id));
        if (!job.getUser().getId().equals(userId)) {
            throw new ImportJobNotFoundException(id);
        }
        return job;
    }

    private void requireStatus(ImportJob job, ImportStatus required) {
        if (job.getStatus() != required) {
            throw new IllegalImportStateException(
                    "Job " + job.getId() + " must be " + required + " but is " + job.getStatus());
        }
    }

    private ImportJobResponse toResponse(ImportJob job) {
        return ImportJobResponse.builder()
                .id(job.getId())
                .platform(job.getPlatform())
                .importType(job.getImportType())
                .status(job.getStatus())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .itemsProcessed(job.getItemsProcessed())
                .currentPage(job.getCurrentPage())
                .totalPages(job.getTotalPages())
                .errorCount(job.getErrorCount())
                .errorMessage(job.getErrorMessage())
                .lastError(job.getLastError())
                .ao3Username(job.getAo3Username())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
