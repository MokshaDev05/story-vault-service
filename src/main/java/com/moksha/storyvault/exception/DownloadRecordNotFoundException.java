package com.moksha.storyvault.exception;

public class DownloadRecordNotFoundException extends RuntimeException {

    public DownloadRecordNotFoundException(Long id) {
        super("Download record not found: " + id);
    }
}
