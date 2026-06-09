package com.moksha.storyvault.dto;

import com.moksha.storyvault.model.enums.FileType;
import com.moksha.storyvault.model.enums.Platform;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class DownloadFilterRequest {
    private FileType fileType;
    private Platform platform;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String fandom;
    private String author;
}
