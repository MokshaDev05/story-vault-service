package com.moksha.storyvault.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class UserPreferencesResponse {
    private String language;
    private List<LanguageOption> supportedLanguages;

    @Getter
    @Builder
    public static class LanguageOption {
        private String code;
        private String label;
        private boolean available;
    }
}
