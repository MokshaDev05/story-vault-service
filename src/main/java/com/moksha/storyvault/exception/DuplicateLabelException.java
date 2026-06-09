package com.moksha.storyvault.exception;

public class DuplicateLabelException extends RuntimeException {

    public DuplicateLabelException(String name) {
        super("Label '" + name + "' already exists");
    }
}
