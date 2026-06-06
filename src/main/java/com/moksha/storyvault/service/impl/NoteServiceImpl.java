package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.NoteRequest;
import com.moksha.storyvault.dto.NoteResponse;
import com.moksha.storyvault.exception.StoryNotFoundException;
import com.moksha.storyvault.model.Note;
import com.moksha.storyvault.repository.NoteRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class NoteServiceImpl implements NoteService {

    private final NoteRepository noteRepository;
    private final StoryRepository storyRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public NoteResponse addNote(Long storyId, NoteRequest request) {
        var user = securityUtils.currentUser();
        var story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        Note note = Note.builder()
                .story(story)
                .content(request.getContent())
                .build();

        return toResponse(noteRepository.save(note));
    }

    @Override
    public List<NoteResponse> getNotes(Long storyId) {
        var user = securityUtils.currentUser();
        if (storyRepository.findByIdAndUser(storyId, user).isEmpty()) {
            throw new StoryNotFoundException(storyId);
        }
        return noteRepository.findByStoryIdOrderByCreatedAtDesc(storyId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private NoteResponse toResponse(Note note) {
        return NoteResponse.builder()
                .id(note.getId())
                .content(note.getContent())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
