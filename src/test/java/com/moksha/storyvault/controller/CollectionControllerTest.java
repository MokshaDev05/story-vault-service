package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.repository.ShelfRepository;
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
class CollectionControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired ShelfRepository shelfRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();
        userA = userRepository.save(User.builder()
                .username("coll-a-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        userB = userRepository.save(User.builder()
                .username("coll-b-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        tokenA = jwtService.generateToken(userA.getUsername());
        tokenB = jwtService.generateToken(userB.getUsername());
    }

    @AfterEach
    void tearDown() {
        cleanUser(userA, tokenA);
        cleanUser(userB, tokenB);
    }

    private void cleanUser(User user, String token) {
        if (user == null) return;
        shelfRepository.findAllWithStoriesByUser(user).forEach(shelfRepository::delete);
        storyRepository.findAllWithTagsByUser(user).forEach(storyRepository::delete);
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

    private Long createCollection(String name, String token) {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/collections"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"" + name + "\"}", auth(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Long.valueOf(((Map<?, ?>) resp.getBody().get("data")).get("id").toString());
    }

    private Long createStory(String workId, String token) {
        String body = """
                {"title":"Test Story %s","author":"Author","fandom":"Fandom",
                 "platform":"AO3","sourceWorkId":"%s",
                 "originalUrl":"https://archiveofourown.org/works/%s"}
                """.formatted(workId, workId, workId);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(body, auth(token)), Map.class);
        return Long.valueOf(((Map<?, ?>) resp.getBody().get("data")).get("id").toString());
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    void create_returns_201_with_id_and_name() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/collections"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Favorites\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("id")).isNotNull();
        assertThat(data.get("name")).isEqualTo("Favorites");
        assertThat(data.get("storyCount")).isEqualTo(0);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Test
    void list_returns_only_current_user_collections() {
        createCollection("UserA List", tokenA);
        createCollection("UserB List", tokenB);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/collections"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> list = (List<?>) resp.getBody().get("data");
        assertThat(list).hasSize(1);
        assertThat(((Map<?, ?>) list.get(0)).get("name")).isEqualTo("UserA List");
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    @Test
    void rename_updates_name_and_returns_200() {
        Long id = createCollection("Old Name", tokenA);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/collections/" + id), HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"New Name\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) resp.getBody().get("data")).get("name")).isEqualTo("New Name");
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_returns_204_and_collection_is_gone() {
        Long id = createCollection("To Delete", tokenA);

        ResponseEntity<Void> del = rest.exchange(
                url("/api/v1/collections/" + id), HttpMethod.DELETE,
                new HttpEntity<>(auth(tokenA)), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> get = rest.exchange(
                url("/api/v1/collections/" + id), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_collection_does_not_delete_stories() {
        Long collId = createCollection("Temp Shelf", tokenA);
        Long storyId = createStory("del-" + System.nanoTime(), tokenA);

        rest.exchange(url("/api/v1/collections/" + collId + "/stories/" + storyId),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        rest.exchange(url("/api/v1/collections/" + collId), HttpMethod.DELETE,
                new HttpEntity<>(auth(tokenA)), Void.class);

        ResponseEntity<Map> storyResp = rest.exchange(
                url("/api/v1/stories/" + storyId), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);
        assertThat(storyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) storyResp.getBody().get("data")).get("title")).isNotNull();
    }

    // ── Add / Remove story ────────────────────────────────────────────────────

    @Test
    void add_story_to_collection_increments_story_count() {
        Long collId = createCollection("Reading List", tokenA);
        Long storyId = createStory("add-" + System.nanoTime(), tokenA);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/collections/" + collId + "/stories/" + storyId),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) resp.getBody().get("data")).get("storyCount")).isEqualTo(1);
    }

    @Test
    void remove_story_from_collection_decrements_story_count() {
        Long collId = createCollection("Shelf", tokenA);
        Long storyId = createStory("rem-" + System.nanoTime(), tokenA);

        rest.exchange(url("/api/v1/collections/" + collId + "/stories/" + storyId),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        ResponseEntity<Void> del = rest.exchange(
                url("/api/v1/collections/" + collId + "/stories/" + storyId),
                HttpMethod.DELETE, new HttpEntity<>(auth(tokenA)), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> get = rest.exchange(
                url("/api/v1/collections/" + collId), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);
        assertThat(((Map<?, ?>) get.getBody().get("data")).get("storyCount")).isEqualTo(0);
    }

    // ── Filter by collection ──────────────────────────────────────────────────

    @Test
    void advanced_search_filters_by_collection_id() {
        Long collId  = createCollection("Favourites", tokenA);
        Long storyIn  = createStory("in-"  + System.nanoTime(), tokenA);
        Long storyOut = createStory("out-" + System.nanoTime(), tokenA);

        rest.exchange(url("/api/v1/collections/" + collId + "/stories/" + storyIn),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/search"), HttpMethod.POST,
                new HttpEntity<>("{\"collectionId\":" + collId + "}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> results = (List<?>) resp.getBody().get("data");
        assertThat(results).hasSize(1);
        assertThat(((Map<?, ?>) results.get(0)).get("id").toString()).isEqualTo(storyIn.toString());
        // storyOut must not appear
        boolean hasOut = results.stream().anyMatch(r -> ((Map<?,?>) r).get("id").toString().equals(storyOut.toString()));
        assertThat(hasOut).isFalse();
    }

    // ── Cross-user isolation ──────────────────────────────────────────────────

    @Test
    void user_B_cannot_access_user_A_collection() {
        Long id = createCollection("Private", tokenA);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/collections/" + id), HttpMethod.GET,
                new HttpEntity<>(auth(tokenB)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void user_B_cannot_rename_user_A_collection() {
        Long id = createCollection("Mine", tokenA);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/collections/" + id), HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"Hijacked\"}", auth(tokenB)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void user_B_cannot_add_story_to_user_A_collection() {
        Long collId  = createCollection("Protected", tokenA);
        Long storyId = createStory("sec-" + System.nanoTime(), tokenB);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/collections/" + collId + "/stories/" + storyId),
                HttpMethod.POST, new HttpEntity<>(auth(tokenB)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
