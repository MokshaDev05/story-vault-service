package com.moksha.storyvault.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NoteRequest {

    @NotBlank(message = "Note content is required")
    private String content;
}
