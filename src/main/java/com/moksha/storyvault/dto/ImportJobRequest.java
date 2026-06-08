package com.moksha.storyvault.dto;

import com.moksha.storyvault.model.enums.ImportType;
import com.moksha.storyvault.model.enums.Platform;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ImportJobRequest {

    @NotNull(message = "Platform is required")
    private Platform platform;

    @NotNull(message = "Import type is required")
    private ImportType importType;
}
