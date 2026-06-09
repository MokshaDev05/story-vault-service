package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.User;
import com.moksha.storyvault.repository.UserRepository;
import com.moksha.storyvault.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PreferencesControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();
        userA = userRepository.save(User.builder()
                .username("pref-a-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        userB = userRepository.save(User.builder()
                .username("pref-b-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        tokenA = jwtService.generateToken(userA.getUsername());
        tokenB = jwtService.generateToken(userB.getUsername());
    }

    @AfterEach
    void tearDown() {
        userRepository.delete(userA);
        userRepository.delete(userB);
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // ── Default language ──────────────────────────────────────────────────────

    @Test
    void get_returns_english_by_default() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/preferences"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("language")).isEqualTo("en");
    }

    @Test
    void response_includes_supported_languages_list() {
        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/preferences"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");

        List<?> langs = (List<?>) data.get("supportedLanguages");
        assertThat(langs).hasSize(9);
        Map<?, ?> en = (Map<?, ?>) langs.get(0);
        assertThat(en.get("code")).isEqualTo("en");
        assertThat(en.get("label")).isEqualTo("English");
        assertThat(en.get("available")).isEqualTo(true);
        Map<?, ?> es = (Map<?, ?>) langs.get(1);
        assertThat(es.get("available")).isEqualTo(false);
    }

    // ── Persist language ──────────────────────────────────────────────────────

    @Test
    void update_language_persists() {
        rest.exchange(url("/api/v1/preferences"), HttpMethod.PUT,
                new HttpEntity<>("{\"language\":\"ja\"}", auth(tokenA)), Map.class);

        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/preferences"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        assertThat(data.get("language")).isEqualTo("ja");
    }

    @Test
    void update_returns_new_language_in_response() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/preferences"), HttpMethod.PUT,
                new HttpEntity<>("{\"language\":\"ko\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("language")).isEqualTo("ko");
    }

    // ── Fallback to English ───────────────────────────────────────────────────

    @Test
    void get_falls_back_to_english_when_stored_code_is_invalid() {
        // Bypass validation by writing directly to DB
        userA.setPreferredLanguage("zz_bad");
        userRepository.save(userA);

        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/preferences"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        assertThat(data.get("language")).isEqualTo("en");
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void update_with_unknown_language_code_returns_400() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/preferences"), HttpMethod.PUT,
                new HttpEntity<>("{\"language\":\"xx\"}", auth(tokenA)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_with_missing_language_returns_400() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/preferences"), HttpMethod.PUT,
                new HttpEntity<>("{}", auth(tokenA)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Translation key lookup (via supported languages list) ─────────────────

    @Test
    void all_supported_language_codes_are_returned() {
        List<?> langs = (List<?>) ((Map<?, ?>) rest.exchange(url("/api/v1/preferences"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class)
                .getBody().get("data")).get("supportedLanguages");

        List<String> codes = langs.stream()
                .map(l -> ((Map<?, ?>) l).get("code").toString())
                .toList();
        assertThat(codes).containsExactly("en", "es", "fr", "de", "it", "pt", "ja", "ko", "zh");
    }

    // ── User isolation ────────────────────────────────────────────────────────

    @Test
    void language_preference_is_user_scoped() {
        rest.exchange(url("/api/v1/preferences"), HttpMethod.PUT,
                new HttpEntity<>("{\"language\":\"zh\"}", auth(tokenA)), Map.class);

        Map<?, ?> dataA = (Map<?, ?>) rest.exchange(url("/api/v1/preferences"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        Map<?, ?> dataB = (Map<?, ?>) rest.exchange(url("/api/v1/preferences"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenB)), Map.class).getBody().get("data");

        assertThat(dataA.get("language")).isEqualTo("zh");
        assertThat(dataB.get("language")).isEqualTo("en");
    }
}
