package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.User;
import com.moksha.storyvault.repository.LabelRepository;
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
class PersonalNoteAndLabelControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired LabelRepository labelRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;
    private Long storyIdA;

    private static final String STORY_BODY = """
            {"title":"Test Story","author":"Author","fandom":"Fandom","platform":"AO3"}""";

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();
        userA = userRepository.save(User.builder()
                .username("nl-a-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        userB = userRepository.save(User.builder()
                .username("nl-b-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        tokenA = jwtService.generateToken(userA.getUsername());
        tokenB = jwtService.generateToken(userB.getUsername());

        // Create a story for userA
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/stories"), HttpMethod.POST,
                new HttpEntity<>(STORY_BODY, auth(tokenA)), Map.class);
        storyIdA = ((Number) ((Map<?, ?>) resp.getBody().get("data")).get("id")).longValue();
    }

    @AfterEach
    void tearDown() {
        storyRepository.findAllWithTagsByUser(userA).forEach(storyRepository::delete);
        storyRepository.findAllWithTagsByUser(userB).forEach(storyRepository::delete);
        labelRepository.findAllByUserOrderByNameAsc(userA).forEach(labelRepository::delete);
        labelRepository.findAllByUserOrderByNameAsc(userB).forEach(labelRepository::delete);
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

    // ── Personal Notes ────────────────────────────────────────────────────────

    @Test
    void create_note_via_patch_endpoint() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"The bookstore Draco fic.\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("personalNotes")).isEqualTo("The bookstore Draco fic.");
    }

    @Test
    void edit_note_replaces_content() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"First note\"}", auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Updated note.\"}", auth(tokenA)), Map.class);

        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("personalNotes")).isEqualTo("Updated note.");
    }

    @Test
    void note_is_returned_in_story_response() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Read during finals week.\"}", auth(tokenA)), Map.class);

        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/stories/" + storyIdA),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");

        assertThat(data.get("personalNotes")).isEqualTo("Read during finals week.");
    }

    @Test
    void search_finds_story_by_note_content() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"The bookstore Draco fic.\"}", auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/stories/search"), HttpMethod.POST,
                new HttpEntity<>("{\"noteContains\":\"bookstore\"}", auth(tokenA)), Map.class);

        List<?> results = (List<?>) resp.getBody().get("data");
        assertThat(results).hasSize(1);
        assertThat(((Map<?, ?>) results.get(0)).get("personalNotes").toString()).contains("bookstore");
    }

    @Test
    void note_preserved_during_metadata_upsert() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Chapter 17 made me cry.\"}", auth(tokenA)), Map.class);

        // Upsert same story (simulates extension auto-update)
        String upsertBody = """
                {"title":"Test Story","author":"Author","fandom":"Fandom","platform":"AO3","wordCount":50000}""";
        rest.exchange(url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(upsertBody, auth(tokenA)), Map.class);

        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/stories/" + storyIdA),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        assertThat(data.get("personalNotes")).isEqualTo("Chapter 17 made me cry.");
    }

    @Test
    void note_cleared_by_null_content() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Some note\"}", auth(tokenA)), Map.class);

        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":null}", auth(tokenA)), Map.class);

        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/stories/" + storyIdA),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        assertThat(data.get("personalNotes")).isNull();
    }

    @Test
    void get_note_returns_has_note_false_when_no_note() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("hasNote")).isEqualTo(false);
        assertThat(data.get("content")).isNull();
        assertThat(data.get("createdAt")).isNull();
    }

    @Test
    void post_creates_note_returns_201_with_note_response() {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"The slowburn was worth it.\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("hasNote")).isEqualTo(true);
        assertThat(data.get("content")).isEqualTo("The slowburn was worth it.");
        assertThat(data.get("storyId")).isEqualTo(storyIdA.intValue());
        assertThat(data.get("createdAt")).isNotNull();
        assertThat(data.get("updatedAt")).isNotNull();
    }

    @Test
    void post_note_returns_409_when_note_already_exists() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"First note.\"}", auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"Second note.\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void get_note_returns_content_and_timestamps_after_post() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"Read during finals week.\"}", auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);

        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("hasNote")).isEqualTo(true);
        assertThat(data.get("content")).isEqualTo("Read during finals week.");
        assertThat(data.get("createdAt")).isNotNull();
        assertThat(data.get("updatedAt")).isNotNull();
    }

    @Test
    void delete_note_returns_204_and_clears_note() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"A note to delete.\"}", auth(tokenA)), Map.class);

        ResponseEntity<Void> del = rest.exchange(
                url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.DELETE,
                new HttpEntity<>(auth(tokenA)), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        assertThat(data.get("hasNote")).isEqualTo(false);
        assertThat(data.get("content")).isNull();
    }

    @Test
    void delete_note_on_story_without_note_returns_204() {
        ResponseEntity<Void> resp = rest.exchange(
                url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.DELETE,
                new HttpEntity<>(auth(tokenA)), Void.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void note_scoped_to_user_other_user_gets_404() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"UserA private note.\"}", auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.GET,
                new HttpEntity<>(auth(tokenB)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void has_note_true_filter_returns_only_stories_with_notes() {
        // storyIdA gets a note
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"Noted.\"}", auth(tokenA)), Map.class);

        // create a second story with no note
        rest.exchange(url("/api/v1/stories"), HttpMethod.POST,
                new HttpEntity<>("{\"title\":\"No Note Story\",\"author\":\"B\",\"fandom\":\"F\",\"platform\":\"AO3\"}",
                        auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/stories/search"), HttpMethod.POST,
                new HttpEntity<>("{\"hasNote\":true}", auth(tokenA)), Map.class);

        List<?> results = (List<?>) resp.getBody().get("data");
        assertThat(results).hasSize(1);
        assertThat(((Map<?, ?>) results.get(0)).get("id")).isEqualTo(storyIdA.intValue());
    }

    @Test
    void has_note_false_filter_returns_only_stories_without_notes() {
        // create a second story that gets the note
        ResponseEntity<Map> resp2 = rest.exchange(url("/api/v1/stories"), HttpMethod.POST,
                new HttpEntity<>("{\"title\":\"Note Story\",\"author\":\"B\",\"fandom\":\"F\",\"platform\":\"AO3\"}",
                        auth(tokenA)), Map.class);
        Long story2Id = ((Number) ((Map<?, ?>) resp2.getBody().get("data")).get("id")).longValue();

        rest.exchange(url("/api/v1/stories/" + story2Id + "/note"), HttpMethod.POST,
                new HttpEntity<>("{\"content\":\"Only story2 has a note.\"}", auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/stories/search"), HttpMethod.POST,
                new HttpEntity<>("{\"hasNote\":false}", auth(tokenA)), Map.class);

        List<?> results = (List<?>) resp.getBody().get("data");
        assertThat(results).hasSize(1);
        assertThat(((Map<?, ?>) results.get(0)).get("id")).isEqualTo(storyIdA.intValue());
    }

    // ── Personal Labels ───────────────────────────────────────────────────────

    @Test
    void create_label_returns_201() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Comfort Read\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("name")).isEqualTo("Comfort Read");
        assertThat(data.get("id")).isNotNull();
    }

    @Test
    void attach_label_to_story_shows_in_response() {
        Long labelId = createLabel(tokenA, "Made Me Cry");

        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/stories/" + storyIdA),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        List<?> labels = (List<?>) data.get("labels");
        assertThat(labels).hasSize(1);
        assertThat(((Map<?, ?>) labels.get(0)).get("name")).isEqualTo("Made Me Cry");
    }

    @Test
    void filter_stories_by_label_id() {
        Long labelId = createLabel(tokenA, "Hall of Fame");
        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        // Create a second story without this label
        rest.exchange(url("/api/v1/stories"), HttpMethod.POST,
                new HttpEntity<>("{\"title\":\"Other\",\"author\":\"A\",\"fandom\":\"F\",\"platform\":\"AO3\"}",
                        auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/stories/search"), HttpMethod.POST,
                new HttpEntity<>("{\"labelId\":" + labelId + "}", auth(tokenA)), Map.class);

        List<?> results = (List<?>) resp.getBody().get("data");
        assertThat(results).hasSize(1);
        assertThat(((Map<?, ?>) results.get(0)).get("id")).isEqualTo(storyIdA.intValue());
    }

    @Test
    void detach_label_removes_from_story() {
        Long labelId = createLabel(tokenA, "Reread Worthy");
        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.DELETE, new HttpEntity<>(auth(tokenA)), Void.class);

        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/stories/" + storyIdA),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        assertThat((List<?>) data.get("labels")).isEmpty();
    }

    @Test
    void delete_label_removes_it_entirely() {
        Long labelId = createLabel(tokenA, "Peak Slow Burn");

        rest.exchange(url("/api/v1/labels/" + labelId), HttpMethod.DELETE,
                new HttpEntity<>(auth(tokenA)), Void.class);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class);
        List<?> labels = (List<?>) resp.getBody().get("data");
        assertThat(labels).isEmpty();
    }

    @Test
    void delete_nonexistent_label_returns_404() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels/99999999"),
                HttpMethod.DELETE, new HttpEntity<>(auth(tokenA)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void user_isolation_labels_not_visible_across_users() {
        // userA creates a label and attaches it to their story
        Long labelId = createLabel(tokenA, "Favorite Ending");
        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        // userB sees no labels
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenB)), Map.class);
        assertThat((List<?>) resp.getBody().get("data")).isEmpty();

        // userB cannot attach userA's label to anything
        ResponseEntity<Map> attach = rest.exchange(
                url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenB)), Map.class);
        assertThat(attach.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void search_by_label_does_not_return_other_users_stories() {
        Long labelId = createLabel(tokenA, "Comfort Read");
        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        // userB searches with the same labelId — should find nothing
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/stories/search"), HttpMethod.POST,
                new HttpEntity<>("{\"labelId\":" + labelId + "}", auth(tokenB)), Map.class);
        List<?> results = (List<?>) resp.getBody().get("data");
        assertThat(results).isEmpty();
    }

    @Test
    void create_label_with_color_returns_color_in_response() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Comfort Read\",\"color\":\"#FF5733\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("color")).isEqualTo("#FF5733");
    }

    @Test
    void update_label_updates_name_and_color() {
        Long labelId = createLabel(tokenA, "Old Name");

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels/" + labelId), HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"New Name\",\"color\":\"#00BFFF\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("name")).isEqualTo("New Name");
        assertThat(data.get("color")).isEqualTo("#00BFFF");
    }

    @Test
    void update_label_clears_color_when_omitted() {
        Long labelId = createLabel(tokenA, "Colorful");
        rest.exchange(url("/api/v1/labels/" + labelId), HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"Colorful\",\"color\":\"#FF0000\"}", auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels/" + labelId), HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"Colorful\"}", auth(tokenA)), Map.class);

        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(data.get("color")).isNull();
    }

    @Test
    void color_propagates_to_story_response_label_summary() {
        Long labelId = createLabelWithColor(tokenA, "Faves", "#GOLD");
        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/stories/" + storyIdA),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        List<?> labels = (List<?>) data.get("labels");
        assertThat(((Map<?, ?>) labels.get(0)).get("color")).isEqualTo("#GOLD");
    }

    @Test
    void duplicate_label_name_returns_409() {
        createLabel(tokenA, "Favorites");

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Favorites\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void duplicate_label_name_is_case_insensitive() {
        createLabel(tokenA, "Slow Burn");

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"slow burn\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rename_to_same_name_different_case_succeeds() {
        Long labelId = createLabel(tokenA, "comfort read");

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels/" + labelId), HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"Comfort Read\"}", auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) resp.getBody().get("data")).get("name")).isEqualTo("Comfort Read");
    }

    @Test
    void add_label_via_story_centric_route_attaches_label() {
        Long labelId = createLabel(tokenA, "Story Route Label");

        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/" + storyIdA + "/labels/" + labelId),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/stories/" + storyIdA),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        List<?> labels = (List<?>) data.get("labels");
        assertThat(labels).hasSize(1);
        assertThat(((Map<?, ?>) labels.get(0)).get("name")).isEqualTo("Story Route Label");
    }

    @Test
    void remove_label_via_story_centric_route_detaches_label() {
        Long labelId = createLabel(tokenA, "To Remove");
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/labels/" + labelId),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        rest.exchange(url("/api/v1/stories/" + storyIdA + "/labels/" + labelId),
                HttpMethod.DELETE, new HttpEntity<>(auth(tokenA)), Void.class);

        Map<?, ?> data = (Map<?, ?>) rest.exchange(url("/api/v1/stories/" + storyIdA),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        assertThat((List<?>) data.get("labels")).isEmpty();
    }

    @Test
    void delete_label_does_not_delete_story() {
        Long labelId = createLabel(tokenA, "Temporary Label");
        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        rest.exchange(url("/api/v1/labels/" + labelId), HttpMethod.DELETE,
                new HttpEntity<>(auth(tokenA)), Void.class);

        ResponseEntity<Map> storyResp = rest.exchange(url("/api/v1/stories/" + storyIdA),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class);
        assertThat(storyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) storyResp.getBody().get("data")).get("id")).isEqualTo(storyIdA.intValue());
        assertThat((List<?>) ((Map<?, ?>) storyResp.getBody().get("data")).get("labels")).isEmpty();
    }

    @Test
    void filter_stories_with_no_labels() {
        // storyIdA has no label
        // create a second story and attach a label to it
        ResponseEntity<Map> resp2 = rest.exchange(url("/api/v1/stories"), HttpMethod.POST,
                new HttpEntity<>("{\"title\":\"Labeled Story\",\"author\":\"A\",\"fandom\":\"F\",\"platform\":\"AO3\"}",
                        auth(tokenA)), Map.class);
        Long labeledStoryId = ((Number) ((Map<?, ?>) resp2.getBody().get("data")).get("id")).longValue();

        Long labelId = createLabel(tokenA, "Has Label");
        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + labeledStoryId),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        ResponseEntity<Map> searchResp = rest.exchange(url("/api/v1/stories/search"), HttpMethod.POST,
                new HttpEntity<>("{\"noLabels\":true}", auth(tokenA)), Map.class);

        List<?> results = (List<?>) searchResp.getBody().get("data");
        assertThat(results).hasSize(1);
        assertThat(((Map<?, ?>) results.get(0)).get("id")).isEqualTo(storyIdA.intValue());
    }

    @Test
    void filter_stories_by_multiple_label_ids_returns_union() {
        Long labelA = createLabel(tokenA, "Label A");
        Long labelB = createLabel(tokenA, "Label B");

        // story1 gets labelA, story2 gets labelB
        ResponseEntity<Map> resp2 = rest.exchange(url("/api/v1/stories"), HttpMethod.POST,
                new HttpEntity<>("{\"title\":\"Story Two\",\"author\":\"A\",\"fandom\":\"F\",\"platform\":\"AO3\"}",
                        auth(tokenA)), Map.class);
        Long story2Id = ((Number) ((Map<?, ?>) resp2.getBody().get("data")).get("id")).longValue();

        rest.exchange(url("/api/v1/labels/" + labelA + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);
        rest.exchange(url("/api/v1/labels/" + labelB + "/stories/" + story2Id),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        ResponseEntity<Map> searchResp = rest.exchange(url("/api/v1/stories/search"), HttpMethod.POST,
                new HttpEntity<>("{\"labelIds\":[" + labelA + "," + labelB + "]}", auth(tokenA)), Map.class);

        List<?> results = (List<?>) searchResp.getBody().get("data");
        assertThat(results).hasSize(2);
    }

    @Test
    void filter_by_label_ids_excludes_unlabeled_stories() {
        Long labelId = createLabel(tokenA, "Selected");
        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        // create a second story with no label
        rest.exchange(url("/api/v1/stories"), HttpMethod.POST,
                new HttpEntity<>("{\"title\":\"Unlabeled\",\"author\":\"A\",\"fandom\":\"F\",\"platform\":\"AO3\"}",
                        auth(tokenA)), Map.class);

        ResponseEntity<Map> searchResp = rest.exchange(url("/api/v1/stories/search"), HttpMethod.POST,
                new HttpEntity<>("{\"labelIds\":[" + labelId + "]}", auth(tokenA)), Map.class);

        List<?> results = (List<?>) searchResp.getBody().get("data");
        assertThat(results).hasSize(1);
        assertThat(((Map<?, ?>) results.get(0)).get("id")).isEqualTo(storyIdA.intValue());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long createLabel(String token, String name) {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"" + name + "\"}", auth(token)), Map.class);
        return ((Number) ((Map<?, ?>) resp.getBody().get("data")).get("id")).longValue();
    }

    private Long createLabelWithColor(String token, String name, String color) {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"" + name + "\",\"color\":\"" + color + "\"}", auth(token)), Map.class);
        return ((Number) ((Map<?, ?>) resp.getBody().get("data")).get("id")).longValue();
    }
}
