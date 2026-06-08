package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.User;
import com.moksha.storyvault.repository.DownloadRecordRepository;
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
class DownloadControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired DownloadRecordRepository downloadRecordRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();
        userA = userRepository.save(User.builder()
                .username("dl-a-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        userB = userRepository.save(User.builder()
                .username("dl-b-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        tokenA = jwtService.generateToken(userA.getUsername());
        tokenB = jwtService.generateToken(userB.getUsername());
    }

    @AfterEach
    void tearDown() {
        cleanUser(userA);
        cleanUser(userB);
    }

    private void cleanUser(User user) {
        if (user == null) return;
        storyRepository.findAllWithTagsByUser(user).forEach(s -> storyRepository.delete(s));
        userRepository.delete(user);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private Long createStory(String workId, String token) {
        String body = """
                {"title":"Story %s","author":"Author","fandom":"Fandom",
                 "platform":"AO3","sourceWorkId":"%s",
                 "originalUrl":"https://archiveofourown.org/works/%s"}
                """.formatted(workId, workId, workId);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(body, auth(token)), Map.class);
        return Long.valueOf(((Map<?, ?>) resp.getBody().get("data")).get("id").toString());
    }

    private Long createDownload(Long storyId, String fileType, String token) {
        String body = """
                {"platform":"AO3","fileType":"%s","fileName":"story.%s",
                 "sourceUrl":"https://archiveofourown.org/works/%d/download"}
                """.formatted(fileType, fileType.toLowerCase(), storyId);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyId + "/downloads"), HttpMethod.POST,
                new HttpEntity<>(body, auth(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Long.valueOf(((Map<?, ?>) resp.getBody().get("data")).get("id").toString());
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    void create_returns_201_with_fields() {
        Long storyId = createStory("cr-" + System.nanoTime(), tokenA);
        String body = """
                {"platform":"AO3","fileType":"EPUB","fileName":"work.epub",
                 "sourceUrl":"https://archiveofourown.org/works/123/download",
                 "notes":"Downloaded from AO3"}
                """;

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyId + "/downloads"), HttpMethod.POST,
                new HttpEntity<>(body, auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("id")).isNotNull();
        assertThat(data.get("fileType")).isEqualTo("EPUB");
        assertThat(data.get("platform")).isEqualTo("AO3");
        assertThat(data.get("fileName")).isEqualTo("work.epub");
        assertThat(data.get("notes")).isEqualTo("Downloaded from AO3");
        assertThat(data.get("storyId").toString()).isEqualTo(storyId.toString());
        assertThat(data.get("downloadedAt")).isNotNull();
    }

    // ── List by story ─────────────────────────────────────────────────────────

    @Test
    void list_by_story_returns_records() {
        Long storyId = createStory("ls-" + System.nanoTime(), tokenA);
        createDownload(storyId, "EPUB", tokenA);
        createDownload(storyId, "PDF", tokenA);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyId + "/downloads"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> list = (List<?>) resp.getBody().get("data");
        assertThat(list).hasSize(2);
    }

    // ── List all user downloads ───────────────────────────────────────────────

    @Test
    void list_all_returns_downloads_across_stories() {
        Long story1 = createStory("la1-" + System.nanoTime(), tokenA);
        Long story2 = createStory("la2-" + System.nanoTime(), tokenA);
        createDownload(story1, "EPUB", tokenA);
        createDownload(story2, "PDF",  tokenA);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/downloads"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> list = (List<?>) resp.getBody().get("data");
        assertThat(list).hasSize(2);
    }

    // ── Story can have multiple download records ───────────────────────────────

    @Test
    void story_can_have_multiple_download_records_with_different_file_types() {
        Long storyId = createStory("multi-" + System.nanoTime(), tokenA);
        createDownload(storyId, "EPUB", tokenA);
        createDownload(storyId, "PDF",  tokenA);
        createDownload(storyId, "HTML", tokenA);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyId + "/downloads"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);

        List<?> list = (List<?>) resp.getBody().get("data");
        assertThat(list).hasSize(3);
        List<String> types = list.stream()
                .map(r -> ((Map<?, ?>) r).get("fileType").toString())
                .toList();
        assertThat(types).containsExactlyInAnyOrder("EPUB", "PDF", "HTML");
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returns_204_and_record_is_gone() {
        Long storyId = createStory("del-" + System.nanoTime(), tokenA);
        Long dlId    = createDownload(storyId, "EPUB", tokenA);

        ResponseEntity<Void> del = rest.exchange(
                url("/api/v1/stories/" + storyId + "/downloads/" + dlId), HttpMethod.DELETE,
                new HttpEntity<>(auth(tokenA)), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> list = rest.exchange(
                url("/api/v1/stories/" + storyId + "/downloads"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);
        assertThat(((List<?>) list.getBody().get("data"))).isEmpty();
    }

    @Test
    void delete_download_does_not_delete_story() {
        Long storyId = createStory("delstory-" + System.nanoTime(), tokenA);
        Long dlId    = createDownload(storyId, "PDF", tokenA);

        rest.exchange(url("/api/v1/stories/" + storyId + "/downloads/" + dlId),
                HttpMethod.DELETE, new HttpEntity<>(auth(tokenA)), Void.class);

        ResponseEntity<Map> storyResp = rest.exchange(
                url("/api/v1/stories/" + storyId), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);
        assertThat(storyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) storyResp.getBody().get("data")).get("title")).isNotNull();
    }

    // ── User isolation ────────────────────────────────────────────────────────

    @Test
    void user_B_cannot_list_user_A_story_downloads() {
        Long storyId = createStory("iso-" + System.nanoTime(), tokenA);
        createDownload(storyId, "EPUB", tokenA);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyId + "/downloads"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenB)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void user_B_cannot_delete_user_A_download() {
        Long storyId = createStory("isodel-" + System.nanoTime(), tokenA);
        Long dlId    = createDownload(storyId, "EPUB", tokenA);

        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/stories/" + storyId + "/downloads/" + dlId), HttpMethod.DELETE,
                new HttpEntity<>(auth(tokenB)), Void.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void all_downloads_list_is_user_scoped() {
        Long storyA = createStory("scopeA-" + System.nanoTime(), tokenA);
        Long storyB = createStory("scopeB-" + System.nanoTime(), tokenB);
        createDownload(storyA, "EPUB", tokenA);
        createDownload(storyB, "PDF",  tokenB);

        ResponseEntity<Map> respA = rest.exchange(
                url("/api/v1/downloads"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);
        List<?> listA = (List<?>) respA.getBody().get("data");
        assertThat(listA).hasSize(1);
        assertThat(((Map<?, ?>) listA.get(0)).get("fileType")).isEqualTo("EPUB");

        ResponseEntity<Map> respB = rest.exchange(
                url("/api/v1/downloads"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenB)), Map.class);
        List<?> listB = (List<?>) respB.getBody().get("data");
        assertThat(listB).hasSize(1);
        assertThat(((Map<?, ?>) listB.get(0)).get("fileType")).isEqualTo("PDF");
    }
}
