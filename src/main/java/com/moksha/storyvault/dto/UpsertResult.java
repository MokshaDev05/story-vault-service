package com.moksha.storyvault.dto;

import java.time.LocalDateTime;

public record UpsertResult(StoryResponse story, boolean created, LocalDateTime priorLastAccessedAt) {}
