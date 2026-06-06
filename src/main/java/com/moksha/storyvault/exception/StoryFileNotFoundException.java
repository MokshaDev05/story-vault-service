package com.moksha.storyvault.exception;

public class StoryFileNotFoundException extends RuntimeException {

    public StoryFileNotFoundException(Long storyId) {
        super("No file found for story with id: " + storyId);
    }
}
