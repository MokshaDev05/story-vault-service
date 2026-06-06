package com.moksha.storyvault.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadRecordResponse {

    private Long id;
    private String source;
    private String notes;
    private LocalDateTime downloadedAt;
}
