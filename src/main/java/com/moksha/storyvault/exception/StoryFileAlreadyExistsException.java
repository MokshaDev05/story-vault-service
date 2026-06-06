package com.moksha.storyvault.exception;

public class StoryFileAlreadyExistsException extends RuntimeException {

    public StoryFileAlreadyExistsException(Long storyId) {
        super("A file already exists for story with id: " + storyId + ". Delete it first before uploading a new one.");
    }
}
