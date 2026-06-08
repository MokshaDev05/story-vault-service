package com.moksha.storyvault.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moksha.storyvault.model.enums.Platform;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectedAccountResponse {

    private Long id;
    private Platform platform;
    private String displayName;
    private String profileUrl;
    private String accountLabel;
    private Boolean syncEnabled;
    private String notes;
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
