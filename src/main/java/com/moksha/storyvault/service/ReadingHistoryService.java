package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.ReadingHistoryRequest;
import com.moksha.storyvault.dto.ReadingHistoryResponse;

import java.util.List;

public interface ReadingHistoryService {

    ReadingHistoryResponse log(Long storyId, ReadingHistoryRequest request);

    List<ReadingHistoryResponse> listByStory(Long storyId);
}
