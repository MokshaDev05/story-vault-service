package com.moksha.storyvault.exception;

public class ShelfNotFoundException extends RuntimeException {
    public ShelfNotFoundException(Long id) {
        super("Collection not found: " + id);
    }
}
