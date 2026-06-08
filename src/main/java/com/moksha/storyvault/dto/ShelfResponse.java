package com.moksha.storyvault.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShelfResponse {
    private Long id;
    private String name;
    private int storyCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
