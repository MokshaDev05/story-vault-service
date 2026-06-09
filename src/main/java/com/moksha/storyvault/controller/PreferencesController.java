package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.UserPreferencesRequest;
import com.moksha.storyvault.dto.UserPreferencesResponse;
import com.moksha.storyvault.dto.UserPreferencesResponse.LanguageOption;
import com.moksha.storyvault.repository.UserRepository;
import com.moksha.storyvault.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
public class PreferencesController {

    private static final Set<String> KNOWN_CODES =
            Set.of("en", "es", "fr", "de", "it", "pt", "ja", "ko", "zh");

    private static final List<LanguageOption> LANGUAGE_OPTIONS = List.of(
            lang("en", "English",   true),
            lang("es", "Español",   false),
            lang("fr", "Français",  false),
            lang("de", "Deutsch",   false),
            lang("it", "Italiano",  false),
            lang("pt", "Português", false),
            lang("ja", "日本語",     false),
            lang("ko", "한국어",     false),
            lang("zh", "中文",       false)
    );

    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<UserPreferencesResponse>> get() {
        var user = securityUtils.currentUser();
        var lang = user.getPreferredLanguage();
        if (lang == null || !KNOWN_CODES.contains(lang)) lang = "en";
        return ResponseEntity.ok(ApiResponse.success("Preferences retrieved",
                UserPreferencesResponse.builder()
                        .language(lang)
                        .supportedLanguages(LANGUAGE_OPTIONS)
                        .build()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserPreferencesResponse>> update(
            @Valid @RequestBody UserPreferencesRequest request) {
        if (!KNOWN_CODES.contains(request.getLanguage())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Unknown language code: " + request.getLanguage()));
        }
        var user = securityUtils.currentUser();
        user.setPreferredLanguage(request.getLanguage());
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("Preferences updated",
                UserPreferencesResponse.builder()
                        .language(user.getPreferredLanguage())
                        .supportedLanguages(LANGUAGE_OPTIONS)
                        .build()));
    }

    private static LanguageOption lang(String code, String label, boolean available) {
        return LanguageOption.builder().code(code).label(label).available(available).build();
    }
}
