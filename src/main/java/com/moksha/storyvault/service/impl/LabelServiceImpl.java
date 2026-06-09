package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.LabelRequest;
import com.moksha.storyvault.dto.LabelResponse;
import com.moksha.storyvault.exception.DuplicateLabelException;
import com.moksha.storyvault.exception.LabelNotFoundException;
import com.moksha.storyvault.exception.StoryNotFoundException;
import com.moksha.storyvault.model.Label;
import com.moksha.storyvault.model.enums.TimelineEventType;
import com.moksha.storyvault.repository.LabelRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.LabelService;
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
public class LabelServiceImpl implements LabelService {

    private final LabelRepository labelRepository;
    private final StoryRepository storyRepository;
    private final SecurityUtils securityUtils;
    private final TimelineService timelineService;

    @Override
    @Transactional
    public LabelResponse create(LabelRequest request) {
        var user = securityUtils.currentUser();
        String name = request.getName().trim();
        if (labelRepository.existsByNameIgnoreCaseAndUser(name, user)) {
            throw new DuplicateLabelException(name);
        }
        Label label = Label.builder()
                .user(user)
                .name(name)
                .color(request.getColor())
                .build();
        return toResponse(labelRepository.save(label));
    }

    @Override
    public List<LabelResponse> listAll() {
        var user = securityUtils.currentUser();
        return labelRepository.findAllByUserOrderByNameAsc(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public LabelResponse update(Long id, LabelRequest request) {
        var user = securityUtils.currentUser();
        Label label = labelRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new LabelNotFoundException(id));
        String newName = request.getName().trim();
        if (!label.getName().equalsIgnoreCase(newName)
                && labelRepository.existsByNameIgnoreCaseAndUser(newName, user)) {
            throw new DuplicateLabelException(newName);
        }
        label.setName(newName);
        label.setColor(request.getColor());
        return toResponse(labelRepository.save(label));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        var user = securityUtils.currentUser();
        Label label = labelRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new LabelNotFoundException(id));
        // Remove the label from every story's labels set so Hibernate flushes the
        // join table deletes before issuing the DELETE on the label row itself.
        new java.util.HashSet<>(label.getStories())
                .forEach(story -> story.getLabels().remove(label));
        labelRepository.delete(label);
    }

    @Override
    @Transactional
    public LabelResponse attachStory(Long labelId, Long storyId) {
        var user = securityUtils.currentUser();
        Label label = labelRepository.findByIdAndUser(labelId, user)
                .orElseThrow(() -> new LabelNotFoundException(labelId));
        var story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        story.getLabels().add(label);
        storyRepository.save(story);
        timelineService.record(user, story, TimelineEventType.PERSONAL_LABEL_ADDED,
                Map.of("storyTitle", story.getTitle(), "fandom", story.getFandom(),
                       "platform", story.getPlatform().name(), "labelName", label.getName()));
        return toResponse(labelRepository.findById(labelId).orElse(label));
    }

    @Override
    @Transactional
    public LabelResponse detachStory(Long labelId, Long storyId) {
        var user = securityUtils.currentUser();
        Label label = labelRepository.findByIdAndUser(labelId, user)
                .orElseThrow(() -> new LabelNotFoundException(labelId));
        var story = storyRepository.findByIdAndUser(storyId, user)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        story.getLabels().removeIf(l -> l.getId().equals(labelId));
        storyRepository.save(story);
        return toResponse(label);
    }

    private LabelResponse toResponse(Label label) {
        return LabelResponse.builder()
                .id(label.getId())
                .name(label.getName())
                .color(label.getColor())
                .storyCount(label.getStories().size())
                .createdAt(label.getCreatedAt())
                .updatedAt(label.getUpdatedAt())
                .build();
    }
}
