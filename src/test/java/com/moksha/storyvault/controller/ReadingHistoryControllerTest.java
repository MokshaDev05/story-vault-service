package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.StoryStatus;
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
class ReadingHistoryControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User user;
    private Story story;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("ctrl-rh-" + System.nanoTime())
                .password(passwordEncoder.encode("pass"))
                .build());
        story = storyRepository.save(Story.builder()
                .title("Test Story").author("Test Author").fandom("Test Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .originalUrl("https://archiveofourown.org/works/99999")
                .user(user).build());
        token = jwtService.generateToken(user.getUsername());
    }

    @AfterEach
    void tearDown() {
        if (user == null) return;
        // Story cascade (CascadeType.ALL on Story.readingHistory) deletes history rows first
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

    // ── Auto-log endpoint ─────────────────────────────────────────────────────

    @Test
    void log_access_returns_200_and_persists_entry_fields() {
        String body = """
                {"chapterNumber":7,"chapterTitle":"Into the Storm",
                 "chapterUrl":"https://archiveofourown.org/works/99999/chapters/456",
                 "sourcePlatform":"AO3","chapterAo3Id":"456","readingMode":"CHAPTER","eventType":"PAGE_LOAD"}
                """;

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + story.getId() + "/access"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("chapterNumber")).isEqualTo(7);
        assertThat(data.get("chapterTitle")).isEqualTo("Into the Storm");
        assertThat(data.get("readingMode")).isEqualTo("CHAPTER");
        assertThat(data.get("chapterAo3Id")).isEqualTo("456");
        assertThat(data.get("eventType")).isEqualTo("PAGE_LOAD");
        assertThat(data.get("storyId")).isEqualTo(story.getId().intValue());
    }

    @Test
    void get_history_returns_all_entries_newest_first() {
        // Log chapter 1 first, then chapter 2 (different chapter avoids dedup)
        String ch1 = """
                {"chapterNumber":1,"chapterAo3Id":"100","readingMode":"CHAPTER",
                 "sourcePlatform":"AO3","eventType":"PAGE_LOAD"}
                """;
        String ch2 = """
                {"chapterNumber":2,"chapterAo3Id":"200","readingMode":"CHAPTER",
                 "sourcePlatform":"AO3","eventType":"PAGE_LOAD"}
                """;
        rest.exchange(url("/api/v1/stories/" + story.getId() + "/access"),
                HttpMethod.POST, new HttpEntity<>(ch1, authHeaders()), Map.class);
        rest.exchange(url("/api/v1/stories/" + story.getId() + "/access"),
                HttpMethod.POST, new HttpEntity<>(ch2, authHeaders()), Map.class);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + story.getId() + "/access"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> list = (List<?>) resp.getBody().get("data");
        assertThat(list).hasSize(2);
        // Newest first: chapter 2 at index 0, chapter 1 at index 1
        assertThat(((Map<?, ?>) list.get(0)).get("chapterNumber")).isEqualTo(2);
        assertThat(((Map<?, ?>) list.get(1)).get("chapterNumber")).isEqualTo(1);
    }

    @Test
    void log_access_without_token_returns_401_or_403() {
        HttpHeaders noAuth = new HttpHeaders();
        noAuth.setContentType(MediaType.APPLICATION_JSON);
        String body = """
                {"chapterNumber":1,"readingMode":"CHAPTER","sourcePlatform":"AO3","eventType":"PAGE_LOAD"}
                """;

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + story.getId() + "/access"),
                HttpMethod.POST,
                new HttpEntity<>(body, noAuth),
                Map.class);

        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void full_work_mode_stores_null_chapter_number() {
        String body = """
                {"chapterNumber":null,"readingMode":"FULL_WORK",
                 "sourcePlatform":"AO3","eventType":"PAGE_LOAD"}
                """;

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + story.getId() + "/access"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("chapterNumber")).isNull();
        assertThat(data.get("readingMode")).isEqualTo("FULL_WORK");
    }

    @Test
    void chapter_title_and_number_are_stored_and_returned_as_separate_fields() {
        String body = """
                {"chapterNumber":3,"chapterTitle":"The Reckoning",
                 "readingMode":"CHAPTER","sourcePlatform":"AO3","eventType":"PAGE_LOAD"}
                """;

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + story.getId() + "/access"),
                HttpMethod.POST,
                new HttpEntity<>(body, authHeaders()),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("chapterNumber")).isEqualTo(3);
        assertThat(data.get("chapterTitle")).isEqualTo("The Reckoning");
    }
}
