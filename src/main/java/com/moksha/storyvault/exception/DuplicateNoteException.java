package com.moksha.storyvault.exception;

public class DuplicateNoteException extends RuntimeException {

    public DuplicateNoteException(Long storyId) {
        super("Story " + storyId + " already has a note");
    }
}
