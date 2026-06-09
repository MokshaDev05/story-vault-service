package com.moksha.storyvault.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ImportErrorRequest {

    @NotBlank
    private String errorMessage;
}
