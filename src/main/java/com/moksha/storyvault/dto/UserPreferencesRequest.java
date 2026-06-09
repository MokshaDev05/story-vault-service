package com.moksha.storyvault.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserPreferencesRequest {

    @NotBlank(message = "Language code is required")
    private String language;
}
