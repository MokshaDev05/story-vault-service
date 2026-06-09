package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.ShelfRequest;
import com.moksha.storyvault.dto.ShelfResponse;
import com.moksha.storyvault.exception.ShelfNotFoundException;
import com.moksha.storyvault.exception.StoryNotFoundException;
import com.moksha.storyvault.model.Shelf;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.enums.TimelineEventType;
import com.moksha.storyvault.repository.ShelfRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.ShelfService;
import com.moksha.storyvault.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ShelfServiceImpl implements ShelfService {

    private final ShelfRepository shelfRepository;
    private final StoryRepository storyRepository;
    private final SecurityUtils securityUtils;
    private final TimelineService timelineService;

    @Override
    @Transactional
    public ShelfResponse create(ShelfRequest request) {
        var user = securityUtils.currentUser();
        Shelf shelf = Shelf.builder()
                .user(user)
                .name(request.getName().trim())
                .build();
        return toResponse(shelfRepository.save(shelf));
    }

    @Override
    public List<ShelfResponse> listAll() {
        var user = securityUtils.currentUser();
        return shelfRepository.findAllWithStoriesByUser(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ShelfResponse findById(Long id) {
        var user = securityUtils.currentUser();
        return shelfRepository.findByIdAndUser(id, user)
                .map(this::toResponse)
                .orElseThrow(() -> new ShelfNotFoundException(id));
    }

    @Override
    @Transactional
    public ShelfResponse rename(Long id, ShelfRequest request) {
        var user = securityUtils.currentUser();
        Shelf shelf = shelfRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ShelfNotFoundException(id));
        shelf.setName(request.getName().trim());
        return toResponse(shelfRepository.save(shelf));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        var user = securityUtils.currentUser();
        Shelf shelf = shelfRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ShelfNotFoundException(id));
        shelfRepository.delete(shelf);
    }

    @Override
    @Transactional
    public ShelfResponse addStory(Long shelfId, Long storyId) {
        var user = securityUtils.currentUser();
        Shelf shelf = shelfRepository.findByIdAndUser(shelfId, user)
                .orElseThrow(() -> new ShelfNotFoundException(shelfId));
        Story story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        shelf.getStories().add(story);
        ShelfResponse response = toResponse(shelfRepository.save(shelf));
        timelineService.record(user, story, TimelineEventType.COLLECTION_ADDED,
                Map.of("storyTitle", story.getTitle(), "fandom", story.getFandom(),
                       "platform", story.getPlatform().name(), "collectionName", shelf.getName()));
        return response;
    }

    @Override
    @Transactional
    public void removeStory(Long shelfId, Long storyId) {
        var user = securityUtils.currentUser();
        Shelf shelf = shelfRepository.findByIdAndUser(shelfId, user)
                .orElseThrow(() -> new ShelfNotFoundException(shelfId));
        Story story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        shelf.getStories().removeIf(s -> s.getId().equals(storyId));
        shelfRepository.save(shelf);
        timelineService.record(user, story, TimelineEventType.COLLECTION_REMOVED,
                Map.of("storyTitle", story.getTitle(), "fandom", story.getFandom(),
                       "platform", story.getPlatform().name(), "collectionName", shelf.getName()));
    }

    private ShelfResponse toResponse(Shelf shelf) {
        return ShelfResponse.builder()
                .id(shelf.getId())
                .name(shelf.getName())
                .storyCount(shelf.getStories().size())
                .createdAt(shelf.getCreatedAt())
                .updatedAt(shelf.getUpdatedAt())
                .build();
    }
}
