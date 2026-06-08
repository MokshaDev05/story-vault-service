package com.moksha.storyvault.dto;

import com.moksha.storyvault.model.enums.ImportStatus;
import com.moksha.storyvault.model.enums.ImportType;
import com.moksha.storyvault.model.enums.Platform;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ImportJobResponse {
    private Long id;
    private Platform platform;
    private ImportType importType;
    private ImportStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private int itemsProcessed;
    private String errorMessage;
    private LocalDateTime createdAt;
}
