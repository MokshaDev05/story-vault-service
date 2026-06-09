package com.moksha.storyvault.exception;

public class ImportJobNotFoundException extends RuntimeException {
    public ImportJobNotFoundException(Long id) {
        super("Import job not found: " + id);
    }
}
