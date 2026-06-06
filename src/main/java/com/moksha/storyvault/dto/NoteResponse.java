package com.moksha.storyvault.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteResponse {

    private Long id;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
