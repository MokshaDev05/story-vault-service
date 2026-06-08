package com.moksha.storyvault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShelfRequest {

    @NotBlank(message = "Collection name is required")
    @Size(max = 200, message = "Collection name must not exceed 200 characters")
    private String name;
}
