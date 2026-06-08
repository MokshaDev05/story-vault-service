package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.ImportJobRequest;
import com.moksha.storyvault.dto.ImportJobResponse;
import com.moksha.storyvault.model.ImportJob;
import com.moksha.storyvault.repository.ImportJobRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    private final ImportJobRepository importJobRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public ImportJobResponse create(ImportJobRequest request) {
        var user = securityUtils.currentUser();
        var job = ImportJob.builder()
                .user(user)
                .platform(request.getPlatform())
                .importType(request.getImportType())
                .build();
        return toResponse(importJobRepository.save(job));
    }

    @Override
    public List<ImportJobResponse> listAll() {
        var user = securityUtils.currentUser();
        return importJobRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
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
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .build();
    }
}
