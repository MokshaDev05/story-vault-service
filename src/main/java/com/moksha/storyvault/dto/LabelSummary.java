package com.moksha.storyvault.dto;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelSummary {

    private Long id;
    private String name;
    private String color;
}
