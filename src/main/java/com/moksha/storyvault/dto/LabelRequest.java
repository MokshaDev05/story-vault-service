package com.moksha.storyvault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabelRequest {

    @NotBlank(message = "Label name is required")
    @Size(max = 200, message = "Label name must not exceed 200 characters")
    private String name;

    @Size(max = 20, message = "Color must not exceed 20 characters")
    private String color;
}
