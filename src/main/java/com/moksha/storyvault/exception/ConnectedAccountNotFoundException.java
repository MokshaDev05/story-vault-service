package com.moksha.storyvault.exception;

public class ConnectedAccountNotFoundException extends RuntimeException {
    public ConnectedAccountNotFoundException(Long id) {
        super("Connected account not found: " + id);
    }
}
