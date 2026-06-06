package com.moksha.storyvault.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FileDownloadResponse {

    private String filename;
    private String contentType;
    private long fileSize;
    private byte[] data;
}
