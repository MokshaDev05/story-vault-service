package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.PagedApiResponse;
import com.moksha.storyvault.dto.PersonalNoteResponse;
import com.moksha.storyvault.dto.StoryPublicResponse;
import com.moksha.storyvault.dto.StoryRequest;
import com.moksha.storyvault.dto.StoryResponse;
import com.moksha.storyvault.dto.StorySearchRequest;
import com.moksha.storyvault.dto.UpsertResult;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.StoryStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface StoryService {

    StoryResponse create(StoryRequest request);

    /** Create if new, update AO3 metadata if already known. Never throws DuplicateStoryException. */
    UpsertResult upsert(StoryRequest request);

    List<StoryResponse> findAll();

    PagedApiResponse<StoryResponse> findAllPaged(int page, int size);

    StoryResponse findById(Long id);

    StoryResponse update(Long id, StoryRequest request);

    void delete(Long id);

    List<StoryResponse> search(String fandom, Platform platform, StoryStatus status, Rating rating, String tag);

    PagedApiResponse<StoryResponse> advancedSearch(StorySearchRequest request, int page, int size);

    StoryPublicResponse getPublicView(Long id);

    StoryResponse updatePersonalNote(Long id, String content);

    PersonalNoteResponse getPersonalNote(Long storyId);

    /** Create note; throws DuplicateNoteException if the story already has one. */
    PersonalNoteResponse createPersonalNote(Long storyId, String content);

    void deletePersonalNote(Long storyId);

    void setLastReadDate(Long storyId, LocalDateTime at);

    int repairReadingStatus(boolean force);
}
