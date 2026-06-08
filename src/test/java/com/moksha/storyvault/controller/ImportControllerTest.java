package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.User;
import com.moksha.storyvault.repository.ImportJobRepository;
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
class ImportControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired ImportJobRepository importJobRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();
        userA = userRepository.save(User.builder()
                .username("imp-a-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        userB = userRepository.save(User.builder()
                .username("imp-b-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        tokenA = jwtService.generateToken(userA.getUsername());
        tokenB = jwtService.generateToken(userB.getUsername());
    }

    @AfterEach
    void tearDown() {
        importJobRepository.findAllByUserOrderByCreatedAtDesc(userA).forEach(importJobRepository::delete);
        importJobRepository.findAllByUserOrderByCreatedAtDesc(userB).forEach(importJobRepository::delete);
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

    private ResponseEntity<Map> createJob(String platform, String importType, String token) {
        String body = "{\"platform\":\"" + platform + "\",\"importType\":\"" + importType + "\"}";
        return rest.exchange(url("/api/v1/imports"), HttpMethod.POST,
                new HttpEntity<>(body, auth(token)), Map.class);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    void create_returns_201_with_pending_status() {
        ResponseEntity<Map> resp = createJob("AO3", "HISTORY", tokenA);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("id")).isNotNull();
        assertThat(data.get("platform")).isEqualTo("AO3");
        assertThat(data.get("importType")).isEqualTo("HISTORY");
        assertThat(data.get("status")).isEqualTo("PENDING");
        assertThat(data.get("itemsProcessed")).isEqualTo(0);
        assertThat(data.get("errorMessage")).isNull();
        assertThat(data.get("createdAt")).isNotNull();
    }

    @Test
    void create_validates_required_fields() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/imports"), HttpMethod.POST,
                new HttpEntity<>("{}", auth(tokenA)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Test
    void list_returns_all_jobs_for_user() {
        createJob("AO3", "HISTORY",     tokenA);
        createJob("AO3", "BOOKMARKS",   tokenA);
        createJob("AO3", "SUBSCRIPTIONS", tokenA);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/imports"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) resp.getBody().get("data")).hasSize(3);
    }

    @Test
    void list_returns_empty_when_no_jobs() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/imports"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) resp.getBody().get("data")).isEmpty();
    }

    // ── User isolation ────────────────────────────────────────────────────────

    @Test
    void list_is_user_scoped() {
        createJob("AO3", "HISTORY",   tokenA);
        createJob("AO3", "BOOKMARKS", tokenA);
        createJob("AO3", "HISTORY",   tokenB);

        List<?> listA = (List<?>) rest.exchange(url("/api/v1/imports"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        List<?> listB = (List<?>) rest.exchange(url("/api/v1/imports"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenB)), Map.class).getBody().get("data");

        assertThat(listA).hasSize(2);
        assertThat(listB).hasSize(1);
    }
}
