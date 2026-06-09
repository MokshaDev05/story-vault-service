package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.User;
import com.moksha.storyvault.repository.LabelRepository;
import com.moksha.storyvault.repository.ShelfRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.repository.TimelineEventRepository;
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
class TimelineControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired LabelRepository labelRepository;
    @Autowired ShelfRepository shelfRepository;
    @Autowired TimelineEventRepository timelineEventRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;
    private Long storyIdA;

    private static final String STORY_BODY = """
            {"title":"Timeline Story","author":"Author","fandom":"Harry Potter","platform":"AO3"}""";

    @BeforeEach
    void setUp() {
        long nano = System.nanoTime();
        userA = userRepository.save(User.builder()
                .username("tl-a-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        userB = userRepository.save(User.builder()
                .username("tl-b-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        tokenA = jwtService.generateToken(userA.getUsername());
        tokenB = jwtService.generateToken(userB.getUsername());

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/stories"), HttpMethod.POST,
                new HttpEntity<>(STORY_BODY, auth(tokenA)), Map.class);
        storyIdA = ((Number) ((Map<?, ?>) resp.getBody().get("data")).get("id")).longValue();
    }

    @AfterEach
    void tearDown() {
        timelineEventRepository.deleteAllByUser(userA);
        timelineEventRepository.deleteAllByUser(userB);
        shelfRepository.findAllWithStoriesByUser(userA).forEach(shelfRepository::delete);
        shelfRepository.findAllWithStoriesByUser(userB).forEach(shelfRepository::delete);
        storyRepository.findAllWithTagsByUser(userA).forEach(storyRepository::delete);
        storyRepository.findAllWithTagsByUser(userB).forEach(storyRepository::delete);
        labelRepository.findAllByUserOrderByNameAsc(userA).forEach(labelRepository::delete);
        labelRepository.findAllByUserOrderByNameAsc(userB).forEach(labelRepository::delete);
        userRepository.delete(userA);
        userRepository.delete(userB);
    }

    // ── Event creation ────────────────────────────────────────────────────────

    @Test
    void create_story_generates_first_seen_event() {
        ResponseEntity<Map> resp = fetchTimeline(tokenA, "{}");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> content = (List<?>) ((Map<?, ?>) resp.getBody().get("data")).get("content");
        assertThat(content).isNotEmpty();
        Map<?, ?> event = (Map<?, ?>) content.get(0);
        assertThat(event.get("eventType")).isEqualTo("STORY_FIRST_SEEN");
        assertThat(event.get("storyId")).isNotNull();
    }

    @Test
    void note_update_creates_note_added_event() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Loved this fic.\"}", auth(tokenA)), Map.class);

        List<?> events = timelineContent(tokenA, "{\"eventTypes\":[\"NOTE_ADDED\"]}");
        assertThat(events).hasSize(1);
        Map<?, ?> ev = (Map<?, ?>) events.get(0);
        assertThat(ev.get("eventType")).isEqualTo("NOTE_ADDED");
        assertThat(ev.get("metadata").toString()).contains("Loved this fic");
    }

    @Test
    void second_note_edit_creates_note_edited_event() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"First note.\"}", auth(tokenA)), Map.class);
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Edited note.\"}", auth(tokenA)), Map.class);

        List<?> noteEvents = timelineContent(tokenA, "{\"eventTypes\":[\"NOTE_ADDED\",\"NOTE_EDITED\"]}");
        List<String> types = noteEvents.stream()
                .map(e -> ((Map<?, ?>) e).get("eventType").toString())
                .toList();
        assertThat(types).contains("NOTE_ADDED", "NOTE_EDITED");
    }

    @Test
    void note_edits_are_separate_events_not_overwritten() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Note A.\"}", auth(tokenA)), Map.class);
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Note B.\"}", auth(tokenA)), Map.class);
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Note C.\"}", auth(tokenA)), Map.class);

        List<?> noteEvents = timelineContent(tokenA, "{\"eventTypes\":[\"NOTE_ADDED\",\"NOTE_EDITED\"]}");
        assertThat(noteEvents).hasSize(3);
    }

    @Test
    void label_attach_creates_personal_label_added_event() {
        Long labelId = createLabel(tokenA, "Made Me Cry");
        rest.exchange(url("/api/v1/labels/" + labelId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        List<?> events = timelineContent(tokenA, "{\"eventTypes\":[\"PERSONAL_LABEL_ADDED\"]}");
        assertThat(events).hasSize(1);
        Map<?, ?> ev = (Map<?, ?>) events.get(0);
        assertThat(ev.get("metadata").toString()).contains("Made Me Cry");
    }

    @Test
    void collection_add_creates_collection_added_event() {
        Long shelfId = createShelf(tokenA, "Comfort Reads");
        rest.exchange(url("/api/v1/collections/" + shelfId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);

        List<?> events = timelineContent(tokenA, "{\"eventTypes\":[\"COLLECTION_ADDED\"]}");
        assertThat(events).hasSize(1);
        assertThat(((Map<?, ?>) events.get(0)).get("metadata").toString()).contains("Comfort Reads");
    }

    @Test
    void collection_remove_creates_collection_removed_event() {
        Long shelfId = createShelf(tokenA, "Temp Shelf");
        rest.exchange(url("/api/v1/collections/" + shelfId + "/stories/" + storyIdA),
                HttpMethod.POST, new HttpEntity<>(auth(tokenA)), Map.class);
        rest.exchange(url("/api/v1/collections/" + shelfId + "/stories/" + storyIdA),
                HttpMethod.DELETE, new HttpEntity<>(auth(tokenA)), Void.class);

        List<?> events = timelineContent(tokenA, "{\"eventTypes\":[\"COLLECTION_REMOVED\"]}");
        assertThat(events).hasSize(1);
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    @Test
    void filter_by_event_type_returns_only_matching() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"A note.\"}", auth(tokenA)), Map.class);

        List<?> noteOnly = timelineContent(tokenA, "{\"eventTypes\":[\"NOTE_ADDED\"]}");
        assertThat(noteOnly).allMatch(e -> ((Map<?, ?>) e).get("eventType").equals("NOTE_ADDED"));

        List<?> firstSeenOnly = timelineContent(tokenA, "{\"eventTypes\":[\"STORY_FIRST_SEEN\"]}");
        assertThat(firstSeenOnly).allMatch(e -> ((Map<?, ?>) e).get("eventType").equals("STORY_FIRST_SEEN"));
    }

    @Test
    void search_by_story_title_in_metadata() {
        List<?> events = timelineContent(tokenA, "{\"search\":\"Timeline Story\"}");
        assertThat(events).isNotEmpty();
        assertThat(events).allMatch(e -> ((Map<?, ?>) e).get("metadata").toString().contains("Timeline Story"));
    }

    @Test
    void pagination_works() {
        // Create several extra events via note edits
        for (int i = 1; i <= 5; i++) {
            String body = String.format("{\"content\":\"Note %d\"}", i);
            if (i == 1) {
                rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                        new HttpEntity<>(body, auth(tokenA)), Map.class);
            } else {
                rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                        new HttpEntity<>(body, auth(tokenA)), Map.class);
            }
        }

        Map<?, ?> page1 = (Map<?, ?>) fetchTimeline(tokenA, "{\"page\":0,\"size\":3}").getBody().get("data");
        Map<?, ?> page2 = (Map<?, ?>) fetchTimeline(tokenA, "{\"page\":1,\"size\":3}").getBody().get("data");

        assertThat((List<?>) page1.get("content")).hasSize(3);
        assertThat(((Number) page1.get("totalElements")).intValue()).isGreaterThan(3);
        assertThat((List<?>) page2.get("content")).isNotEmpty();
    }

    @Test
    void events_returned_newest_first() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"First note.\"}", auth(tokenA)), Map.class);
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"Second note.\"}", auth(tokenA)), Map.class);

        List<?> events = timelineContent(tokenA, "{}");
        assertThat(events.size()).isGreaterThanOrEqualTo(2);
        String latest = ((Map<?, ?>) events.get(0)).get("eventTimestamp").toString();
        String older  = ((Map<?, ?>) events.get(1)).get("eventTimestamp").toString();
        assertThat(latest.compareTo(older)).isGreaterThanOrEqualTo(0);
    }

    // ── User isolation ────────────────────────────────────────────────────────

    @Test
    void user_isolation_timeline_not_visible_across_users() {
        // userA has events; userB has none
        List<?> eventsB = timelineContent(tokenB, "{}");
        assertThat(eventsB).isEmpty();
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Test
    void stats_endpoint_returns_correct_counts() {
        rest.exchange(url("/api/v1/stories/" + storyIdA + "/note"), HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"A note.\"}", auth(tokenA)), Map.class);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/timeline/stats"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) resp.getBody().get("data");
        assertThat(((Number) data.get("worksOpened")).longValue()).isEqualTo(1L);
        assertThat(((Number) data.get("notesWritten")).longValue()).isEqualTo(1L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String url(String path) { return "http://localhost:" + port + path; }

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<Map> fetchTimeline(String token, String body) {
        return rest.exchange(url("/api/v1/timeline"), HttpMethod.POST,
                new HttpEntity<>(body, auth(token)), Map.class);
    }

    private List<?> timelineContent(String token, String filterBody) {
        Map<?, ?> data = (Map<?, ?>) fetchTimeline(token, filterBody).getBody().get("data");
        return (List<?>) data.get("content");
    }

    private Long createLabel(String token, String name) {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/labels"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"" + name + "\"}", auth(token)), Map.class);
        return ((Number) ((Map<?, ?>) resp.getBody().get("data")).get("id")).longValue();
    }

    private Long createShelf(String token, String name) {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/collections"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"" + name + "\"}", auth(token)), Map.class);
        return ((Number) ((Map<?, ?>) resp.getBody().get("data")).get("id")).longValue();
    }
}
