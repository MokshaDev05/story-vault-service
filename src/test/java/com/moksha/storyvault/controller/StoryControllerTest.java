package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.repository.StoryRepository;
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
class StoryControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User user;
    private String token;
    private String workId;

    @BeforeEach
    void setUp() {
        workId = "wid-" + System.nanoTime();
        user = userRepository.save(User.builder()
                .username("ctrl-st-" + System.nanoTime())
                .password(passwordEncoder.encode("pass"))
                .build());
        token = jwtService.generateToken(user.getUsername());
    }

    @AfterEach
    void tearDown() {
        if (user == null) return;
        storyRepository.findAllWithTagsByUser(user).forEach(s -> storyRepository.delete(s));
        userRepository.delete(user);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String upsertBody(String title, String wid, Integer chapter, String chapterUrl) {
        String chapterVal = chapter != null ? String.valueOf(chapter) : "null";
        String chapterUrlVal = chapterUrl != null ? "\"" + chapterUrl + "\"" : "null";
        return """
                {"title":"%s","author":"Test Author","fandom":"Test Fandom",
                 "platform":"AO3","sourceWorkId":"%s",
                 "originalUrl":"https://archiveofourown.org/works/%s",
                 "currentChapter":%s,"currentChapterUrl":%s,
                 "readingStatus":"STILL_READING"}
                """.formatted(title, wid, wid, chapterVal, chapterUrlVal);
    }

    // ── Upsert ────────────────────────────────────────────────────────────────

    @Test
    void upsert_new_story_returns_201_with_id() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(upsertBody("New Story", workId, null, null), authHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("id")).isNotNull();
        assertThat(data.get("title")).isEqualTo("New Story");
    }

    @Test
    void upsert_same_workId_returns_200_and_does_not_create_duplicate() {
        rest.exchange(url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(upsertBody("My Story", workId, null, null), authHeaders()), Map.class);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(upsertBody("My Story", workId, 3,
                        "https://archiveofourown.org/works/" + workId + "/chapters/300"), authHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        long count = storyRepository.findAllWithTagsByUser(user).stream()
                .filter(s -> workId.equals(s.getSourceWorkId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void upsert_advances_currentChapterUrl_forward_only() {
        // First visit: chapter 1
        rest.exchange(url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(upsertBody("Progress Story", workId, 1,
                        "https://archiveofourown.org/works/" + workId + "/chapters/100"), authHeaders()),
                Map.class);

        // Second visit: chapter 5 (forward) — should advance
        ResponseEntity<Map> fwd = rest.exchange(url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(upsertBody("Progress Story", workId, 5,
                        "https://archiveofourown.org/works/" + workId + "/chapters/500"), authHeaders()),
                Map.class);
        assertThat(((Map<?, ?>) fwd.getBody().get("data")).get("currentChapterUrl"))
                .isEqualTo("https://archiveofourown.org/works/" + workId + "/chapters/500");

        // Third visit: chapter 2 (backward) — should NOT regress
        ResponseEntity<Map> back = rest.exchange(url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(upsertBody("Progress Story", workId, 2,
                        "https://archiveofourown.org/works/" + workId + "/chapters/200"), authHeaders()),
                Map.class);
        assertThat(((Map<?, ?>) back.getBody().get("data")).get("currentChapterUrl"))
                .isEqualTo("https://archiveofourown.org/works/" + workId + "/chapters/500");
    }

    // ── GET /stories/{id} ─────────────────────────────────────────────────────

    @Test
    void get_by_id_returns_currentChapterUrl_and_originalUrl_for_continue_reading() {
        ResponseEntity<Map> created = rest.exchange(url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(upsertBody("URL Story", workId, 7,
                        "https://archiveofourown.org/works/" + workId + "/chapters/700"), authHeaders()),
                Map.class);
        Long id = Long.valueOf(((Map<?, ?>) created.getBody().get("data")).get("id").toString());

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + id), HttpMethod.GET,
                new HttpEntity<>(authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("currentChapterUrl"))
                .isEqualTo("https://archiveofourown.org/works/" + workId + "/chapters/700");
        assertThat(data.get("originalUrl"))
                .isEqualTo("https://archiveofourown.org/works/" + workId);
    }

    // ── Advanced search ───────────────────────────────────────────────────────

    @Test
    void advanced_search_filters_by_author_contains() {
        String workId2 = workId + "b";
        rest.exchange(url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>("""
                        {"title":"Story One","author":"Alice Aut","fandom":"F","platform":"AO3",
                         "sourceWorkId":"%s","originalUrl":"https://archiveofourown.org/works/%s"}
                        """.formatted(workId, workId), authHeaders()), Map.class);
        rest.exchange(url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>("""
                        {"title":"Story Two","author":"Bob Scribe","fandom":"F","platform":"AO3",
                         "sourceWorkId":"%s","originalUrl":"https://archiveofourown.org/works/%s"}
                        """.formatted(workId2, workId2), authHeaders()), Map.class);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/stories/search"), HttpMethod.POST,
                new HttpEntity<>("""
                        {"authorContains":"alice"}
                        """, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> list = (List<?>) resp.getBody().get("data");
        assertThat(list).hasSize(1);
        assertThat(((Map<?, ?>) list.get(0)).get("title")).isEqualTo("Story One");
    }

    // ── Kudos ─────────────────────────────────────────────────────────────────

    @Test
    void upsert_stores_and_returns_kudosStatus() {
        String body = """
                {"title":"Kudos Story","author":"Author","fandom":"Fandom",
                 "platform":"AO3","sourceWorkId":"%s",
                 "originalUrl":"https://archiveofourown.org/works/%s",
                 "kudosStatus":"GIVEN"}
                """.formatted(workId, workId);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) resp.getBody().get("data")).get("kudosStatus"))
                .isEqualTo("GIVEN");
    }

    @Test
    void upsert_second_call_with_NOT_DETECTED_does_not_overwrite_GIVEN() {
        // First upsert — extension sees "already left kudos"
        String body1 = """
                {"title":"Kudos Story","author":"Author","fandom":"Fandom",
                 "platform":"AO3","sourceWorkId":"%s",
                 "originalUrl":"https://archiveofourown.org/works/%s",
                 "kudosStatus":"GIVEN"}
                """.formatted(workId, workId);
        rest.exchange(url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(body1, authHeaders()), Map.class);

        // Second upsert — extension detects NOT_DETECTED (e.g. re-read from a different page state)
        String body2 = """
                {"title":"Kudos Story","author":"Author","fandom":"Fandom",
                 "platform":"AO3","sourceWorkId":"%s",
                 "originalUrl":"https://archiveofourown.org/works/%s",
                 "kudosStatus":"NOT_DETECTED"}
                """.formatted(workId, workId);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(body2, authHeaders()), Map.class);

        assertThat(((Map<?, ?>) resp.getBody().get("data")).get("kudosStatus"))
                .isEqualTo("GIVEN");
    }

    // ── Unauthenticated ───────────────────────────────────────────────────────

    @Test
    void upsert_without_token_returns_401_or_403() {
        HttpHeaders noAuth = new HttpHeaders();
        noAuth.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(upsertBody("Story", workId, null, null), noAuth), Map.class);

        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }
}
