package com.moksha.storyvault.dto;

import com.moksha.storyvault.model.enums.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectedAccountRequest {

    @NotNull(message = "Platform is required")
    private Platform platform;

    @NotBlank(message = "Display name is required")
    @Size(max = 255)
    private String displayName;

    @Size(max = 2048)
    private String profileUrl;

    @Size(max = 100)
    private String accountLabel;

    @Builder.Default
    private Boolean syncEnabled = true;

    private String notes;
}
