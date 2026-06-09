package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.LabelRequest;
import com.moksha.storyvault.dto.LabelResponse;

import java.util.List;

public interface LabelService {

    LabelResponse create(LabelRequest request);

    List<LabelResponse> listAll();

    LabelResponse update(Long id, LabelRequest request);

    void delete(Long id);

    LabelResponse attachStory(Long labelId, Long storyId);

    LabelResponse detachStory(Long labelId, Long storyId);
}
