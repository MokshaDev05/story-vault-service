package com.moksha.storyvault.dto;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShelfSummary {
    private Long id;
    private String name;
}
