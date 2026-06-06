package com.moksha.storyvault.exception;

import com.moksha.storyvault.dto.StoryResponse;
import lombok.Getter;

@Getter
public class DuplicateStoryException extends RuntimeException {

    private final StoryResponse existingStory;

    public DuplicateStoryException(StoryResponse existingStory) {
        super("This work has already been logged in StoryVault.");
        this.existingStory = existingStory;
    }
}
