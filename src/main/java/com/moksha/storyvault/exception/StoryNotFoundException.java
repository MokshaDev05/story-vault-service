package com.moksha.storyvault.exception;

public class StoryNotFoundException extends RuntimeException {

    public StoryNotFoundException(Long id) {
        super("Story not found with id: " + id);
    }
}
