package com.moksha.storyvault.exception;

public class LabelNotFoundException extends RuntimeException {
    public LabelNotFoundException(Long id) {
        super("Label not found: " + id);
    }
}
