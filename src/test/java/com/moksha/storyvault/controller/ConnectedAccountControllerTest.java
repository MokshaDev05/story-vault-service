package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.User;
import com.moksha.storyvault.repository.ConnectedAccountRepository;
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
class ConnectedAccountControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired ConnectedAccountRepository accountRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();
        userA = userRepository.save(User.builder()
                .username("acct-a-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        userB = userRepository.save(User.builder()
                .username("acct-b-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        tokenA = jwtService.generateToken(userA.getUsername());
        tokenB = jwtService.generateToken(userB.getUsername());
    }

    @AfterEach
    void tearDown() {
        if (userA != null) {
            accountRepository.findAllByUserOrderByCreatedAtAsc(userA).forEach(accountRepository::delete);
            userRepository.delete(userA);
        }
        if (userB != null) {
            accountRepository.findAllByUserOrderByCreatedAtAsc(userB).forEach(accountRepository::delete);
            userRepository.delete(userB);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String createBody(String displayName, String platform) {
        return """
                {"platform":"%s","displayName":"%s","profileUrl":"https://archiveofourown.org/users/%s","syncEnabled":true}
                """.formatted(platform, displayName, displayName);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    void create_returns_201_with_id_and_platform() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/accounts"), HttpMethod.POST,
                new HttpEntity<>(createBody("MyAO3Name", "AO3"), headers(tokenA)),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("id")).isNotNull();
        assertThat(data.get("platform")).isEqualTo("AO3");
        assertThat(data.get("displayName")).isEqualTo("MyAO3Name");
        assertThat(data.get("syncEnabled")).isEqualTo(true);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Test
    void list_returns_only_current_user_accounts() {
        rest.exchange(url("/api/v1/accounts"), HttpMethod.POST,
                new HttpEntity<>(createBody("UserAAccount", "AO3"), headers(tokenA)), Map.class);
        rest.exchange(url("/api/v1/accounts"), HttpMethod.POST,
                new HttpEntity<>(createBody("UserBAccount", "WATTPAD"), headers(tokenB)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/accounts"), HttpMethod.GET,
                new HttpEntity<>(headers(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> list = (List<?>) resp.getBody().get("data");
        assertThat(list).hasSize(1);
        assertThat(((Map<?, ?>) list.get(0)).get("displayName")).isEqualTo("UserAAccount");
    }

    // ── Get by id ─────────────────────────────────────────────────────────────

    @Test
    void get_by_id_returns_account_fields() {
        ResponseEntity<Map> created = rest.exchange(
                url("/api/v1/accounts"), HttpMethod.POST,
                new HttpEntity<>(createBody("ReadAccount", "AO3"), headers(tokenA)), Map.class);
        Long id = Long.valueOf(((Map<?, ?>) created.getBody().get("data")).get("id").toString());

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/accounts/" + id), HttpMethod.GET,
                new HttpEntity<>(headers(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("displayName")).isEqualTo("ReadAccount");
        assertThat(data.get("profileUrl")).isEqualTo("https://archiveofourown.org/users/ReadAccount");
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Test
    void update_changes_display_name_and_sync_flag() {
        ResponseEntity<Map> created = rest.exchange(
                url("/api/v1/accounts"), HttpMethod.POST,
                new HttpEntity<>(createBody("OldName", "AO3"), headers(tokenA)), Map.class);
        Long id = Long.valueOf(((Map<?, ?>) created.getBody().get("data")).get("id").toString());

        String updateBody = """
                {"platform":"AO3","displayName":"NewName","syncEnabled":false}
                """;
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/accounts/" + id), HttpMethod.PUT,
                new HttpEntity<>(updateBody, headers(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("displayName")).isEqualTo("NewName");
        assertThat(data.get("syncEnabled")).isEqualTo(false);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returns_204_and_account_is_gone() {
        ResponseEntity<Map> created = rest.exchange(
                url("/api/v1/accounts"), HttpMethod.POST,
                new HttpEntity<>(createBody("ToDelete", "AO3"), headers(tokenA)), Map.class);
        Long id = Long.valueOf(((Map<?, ?>) created.getBody().get("data")).get("id").toString());

        ResponseEntity<Void> del = rest.exchange(
                url("/api/v1/accounts/" + id), HttpMethod.DELETE,
                new HttpEntity<>(headers(tokenA)), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> get = rest.exchange(
                url("/api/v1/accounts/" + id), HttpMethod.GET,
                new HttpEntity<>(headers(tokenA)), Map.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Cross-user isolation ──────────────────────────────────────────────────

    @Test
    void user_B_cannot_get_user_A_account() {
        ResponseEntity<Map> created = rest.exchange(
                url("/api/v1/accounts"), HttpMethod.POST,
                new HttpEntity<>(createBody("PrivateAccount", "AO3"), headers(tokenA)), Map.class);
        Long id = Long.valueOf(((Map<?, ?>) created.getBody().get("data")).get("id").toString());

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/accounts/" + id), HttpMethod.GET,
                new HttpEntity<>(headers(tokenB)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void user_B_cannot_update_user_A_account() {
        ResponseEntity<Map> created = rest.exchange(
                url("/api/v1/accounts"), HttpMethod.POST,
                new HttpEntity<>(createBody("Protected", "AO3"), headers(tokenA)), Map.class);
        Long id = Long.valueOf(((Map<?, ?>) created.getBody().get("data")).get("id").toString());

        String updateBody = """
                {"platform":"AO3","displayName":"Hacked","syncEnabled":true}
                """;
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/accounts/" + id), HttpMethod.PUT,
                new HttpEntity<>(updateBody, headers(tokenB)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void user_B_cannot_delete_user_A_account() {
        ResponseEntity<Map> created = rest.exchange(
                url("/api/v1/accounts"), HttpMethod.POST,
                new HttpEntity<>(createBody("ShouldStay", "AO3"), headers(tokenA)), Map.class);
        Long id = Long.valueOf(((Map<?, ?>) created.getBody().get("data")).get("id").toString());

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/accounts/" + id), HttpMethod.DELETE,
                new HttpEntity<>(headers(tokenB)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify account still exists for userA
        ResponseEntity<Map> get = rest.exchange(
                url("/api/v1/accounts/" + id), HttpMethod.GET,
                new HttpEntity<>(headers(tokenA)), Map.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Unauthenticated ───────────────────────────────────────────────────────

    @Test
    void list_without_token_returns_401_or_403() {
        HttpHeaders noAuth = new HttpHeaders();
        noAuth.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/accounts"), HttpMethod.GET,
                new HttpEntity<>(noAuth), Map.class);

        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }
}
