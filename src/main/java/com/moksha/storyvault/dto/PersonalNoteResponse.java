package com.moksha.storyvault.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalNoteResponse {

    private Long storyId;
    private String content;
    private boolean hasNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
