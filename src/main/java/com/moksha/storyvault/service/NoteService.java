package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.NoteRequest;
import com.moksha.storyvault.dto.NoteResponse;

import java.util.List;

public interface NoteService {

    NoteResponse addNote(Long storyId, NoteRequest request);

    List<NoteResponse> getNotes(Long storyId);
}
