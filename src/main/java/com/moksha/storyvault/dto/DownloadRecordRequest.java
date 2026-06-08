package com.moksha.storyvault.dto;

import com.moksha.storyvault.model.enums.FileType;
import com.moksha.storyvault.model.enums.Platform;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadRecordRequest {

    private Platform platform;
    private FileType fileType;
    private String fileName;
    private String storageKey;
    private String sourceUrl;
    private String notes;
}
