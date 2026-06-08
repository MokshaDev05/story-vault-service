package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.ShelfRequest;
import com.moksha.storyvault.dto.ShelfResponse;

import java.util.List;

public interface ShelfService {
    ShelfResponse create(ShelfRequest request);
    List<ShelfResponse> listAll();
    ShelfResponse findById(Long id);
    ShelfResponse rename(Long id, ShelfRequest request);
    void delete(Long id);
    ShelfResponse addStory(Long shelfId, Long storyId);
    void removeStory(Long shelfId, Long storyId);
}
