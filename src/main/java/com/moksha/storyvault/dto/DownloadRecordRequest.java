package com.moksha.storyvault.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadRecordRequest {

    private String source;
    private String notes;
}
