package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.User;
import com.moksha.storyvault.repository.ImportJobRepository;
import com.moksha.storyvault.repository.ReadingHistoryRepository;
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
class ImportControllerTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired ImportJobRepository importJobRepository;
    @Autowired StoryRepository storyRepository;
    @Autowired ReadingHistoryRepository readingHistoryRepository;
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
        storyRepository.findAllWithTagsByUser(userA).forEach(storyRepository::delete);
        storyRepository.findAllWithTagsByUser(userB).forEach(storyRepository::delete);
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

    private Long createJobId(String platform, String importType, String token) {
        return ((Number) ((Map<?, ?>) createJob(platform, importType, token).getBody().get("data")).get("id")).longValue();
    }

    private ResponseEntity<Map> transition(Long id, String action, String token) {
        return rest.exchange(url("/api/v1/imports/" + id + "/" + action), HttpMethod.POST,
                new HttpEntity<>(auth(token)), Map.class);
    }

    private Map<?, ?> data(ResponseEntity<Map> resp) {
        return (Map<?, ?>) resp.getBody().get("data");
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Test
    void create_returns_201_with_pending_status() {
        ResponseEntity<Map> resp = createJob("AO3", "HISTORY", tokenA);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> d = data(resp);
        assertThat(d.get("id")).isNotNull();
        assertThat(d.get("platform")).isEqualTo("AO3");
        assertThat(d.get("importType")).isEqualTo("HISTORY");
        assertThat(d.get("status")).isEqualTo("PENDING");
        assertThat(d.get("itemsProcessed")).isEqualTo(0);
        assertThat(d.get("currentPage")).isEqualTo(0);
        assertThat(d.get("errorCount")).isEqualTo(0);
        assertThat(d.get("errorMessage")).isNull();
        assertThat(d.get("createdAt")).isNotNull();
    }

    @Test
    void create_validates_required_fields() {
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/imports"), HttpMethod.POST,
                new HttpEntity<>("{}", auth(tokenA)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Get by ID ─────────────────────────────────────────────────────────────

    @Test
    void get_by_id_returns_job() {
        Long id = createJobId("AO3", "BOOKMARKS", tokenA);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/imports/" + id), HttpMethod.GET,
                new HttpEntity<>(auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(resp).get("id").toString()).isEqualTo(id.toString());
    }

    @Test
    void get_by_id_returns_404_for_other_users_job() {
        Long id = createJobId("AO3", "HISTORY", tokenA);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/imports/" + id), HttpMethod.GET,
                new HttpEntity<>(auth(tokenB)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Test
    void list_returns_all_jobs_for_user() {
        createJob("AO3", "HISTORY",       tokenA);
        createJob("AO3", "BOOKMARKS",     tokenA);
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

    // ── Start ─────────────────────────────────────────────────────────────────

    @Test
    void start_transitions_pending_to_running() {
        Long id = createJobId("AO3", "HISTORY", tokenA);

        ResponseEntity<Map> resp = transition(id, "start", tokenA);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(resp).get("status")).isEqualTo("RUNNING");
        assertThat(data(resp).get("startedAt")).isNotNull();
    }

    @Test
    void start_already_running_returns_conflict() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);

        ResponseEntity<Map> resp = transition(id, "start", tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    @Test
    void progress_updates_page_and_items_processed() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);

        String progressBody = "{\"currentPage\":5,\"totalPages\":50,\"itemsProcessed\":100}";
        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/imports/" + id + "/progress"),
                HttpMethod.POST, new HttpEntity<>(progressBody, auth(tokenA)), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> d = data(resp);
        assertThat(d.get("currentPage")).isEqualTo(5);
        assertThat(((Number) d.get("totalPages")).intValue()).isEqualTo(50);
        assertThat(d.get("itemsProcessed")).isEqualTo(100);
        assertThat(d.get("status")).isEqualTo("RUNNING");
    }

    @Test
    void progress_processes_stories_and_merges_duplicates() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);

        long nano = System.nanoTime();
        String story = """
                {"title":"HP Fic","author":"Author","fandom":"Harry Potter",
                 "platform":"AO3","sourceWorkId":"imp-dup-%d"}
                """.formatted(nano);
        String progressBody = "{\"currentPage\":1,\"totalPages\":10,\"itemsProcessed\":1,\"stories\":[" + story + "]}";

        // Send the same story twice
        rest.exchange(url("/api/v1/imports/" + id + "/progress"),
                HttpMethod.POST, new HttpEntity<>(progressBody, auth(tokenA)), Map.class);
        rest.exchange(url("/api/v1/imports/" + id + "/progress"),
                HttpMethod.POST, new HttpEntity<>(progressBody, auth(tokenA)), Map.class);

        // Verify exactly 1 story was created (no duplicate)
        ResponseEntity<Map> storiesResp = rest.exchange(url("/api/v1/stories"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class);
        List<?> allStories = (List<?>) storiesResp.getBody().get("data");
        long matchCount = allStories.stream()
                .filter(s -> {
                    Object wid = ((Map<?, ?>) s).get("sourceWorkId");
                    return ("imp-dup-" + nano).equals(wid != null ? wid.toString() : null);
                })
                .count();
        assertThat(matchCount).isEqualTo(1);
    }

    @Test
    void progress_on_non_running_job_returns_conflict() {
        Long id = createJobId("AO3", "HISTORY", tokenA);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/imports/" + id + "/progress"),
                HttpMethod.POST,
                new HttpEntity<>("{\"currentPage\":1,\"itemsProcessed\":10}", auth(tokenA)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── Pause / Resume ────────────────────────────────────────────────────────

    @Test
    void pause_transitions_running_to_paused() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);

        // Update progress so currentPage is saved
        rest.exchange(url("/api/v1/imports/" + id + "/progress"),
                HttpMethod.POST,
                new HttpEntity<>("{\"currentPage\":7,\"totalPages\":50,\"itemsProcessed\":140}", auth(tokenA)),
                Map.class);

        ResponseEntity<Map> pauseResp = transition(id, "pause", tokenA);
        assertThat(pauseResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(pauseResp).get("status")).isEqualTo("PAUSED");
    }

    @Test
    void resumable_state_persisted_after_pause() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);
        rest.exchange(url("/api/v1/imports/" + id + "/progress"),
                HttpMethod.POST,
                new HttpEntity<>("{\"currentPage\":12,\"totalPages\":100,\"itemsProcessed\":240}", auth(tokenA)),
                Map.class);
        transition(id, "pause", tokenA);

        // Retrieve the job and verify currentPage was preserved
        ResponseEntity<Map> getResp = rest.exchange(url("/api/v1/imports/" + id),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class);
        Map<?, ?> d = data(getResp);
        assertThat(d.get("status")).isEqualTo("PAUSED");
        assertThat(d.get("currentPage")).isEqualTo(12);
        assertThat(((Number) d.get("totalPages")).intValue()).isEqualTo(100);
    }

    @Test
    void resume_transitions_paused_to_running() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);
        transition(id, "pause", tokenA);

        ResponseEntity<Map> resp = transition(id, "resume", tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(resp).get("status")).isEqualTo("RUNNING");
    }

    @Test
    void pause_non_running_returns_conflict() {
        Long id = createJobId("AO3", "HISTORY", tokenA);

        ResponseEntity<Map> resp = transition(id, "pause", tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void resume_non_paused_returns_conflict() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);

        ResponseEntity<Map> resp = transition(id, "resume", tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Test
    void cancel_pending_job() {
        Long id = createJobId("AO3", "HISTORY", tokenA);

        ResponseEntity<Map> resp = transition(id, "cancel", tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(resp).get("status")).isEqualTo("CANCELLED");
        assertThat(data(resp).get("completedAt")).isNotNull();
    }

    @Test
    void cancel_running_job() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);

        ResponseEntity<Map> resp = transition(id, "cancel", tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(resp).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_paused_job() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);
        transition(id, "pause", tokenA);

        ResponseEntity<Map> resp = transition(id, "cancel", tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(resp).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_completed_job_returns_conflict() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);
        transition(id, "complete", tokenA);

        ResponseEntity<Map> resp = transition(id, "cancel", tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── Complete ──────────────────────────────────────────────────────────────

    @Test
    void complete_transitions_running_to_completed() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);

        ResponseEntity<Map> resp = transition(id, "complete", tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(resp).get("status")).isEqualTo("COMPLETED");
        assertThat(data(resp).get("completedAt")).isNotNull();
    }

    @Test
    void complete_non_running_returns_conflict() {
        Long id = createJobId("AO3", "HISTORY", tokenA);

        ResponseEntity<Map> resp = transition(id, "complete", tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── Error ─────────────────────────────────────────────────────────────────

    @Test
    void error_transitions_running_to_failed() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/imports/" + id + "/error"),
                HttpMethod.POST,
                new HttpEntity<>("{\"errorMessage\":\"Rate limited at page 7\"}", auth(tokenA)),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> d = data(resp);
        assertThat(d.get("status")).isEqualTo("FAILED");
        assertThat(d.get("errorMessage")).isEqualTo("Rate limited at page 7");
        assertThat(((Number) d.get("errorCount")).intValue()).isEqualTo(1);
        assertThat(d.get("completedAt")).isNotNull();
    }

    @Test
    void error_on_non_running_job_returns_conflict() {
        Long id = createJobId("AO3", "HISTORY", tokenA);

        ResponseEntity<Map> resp = rest.exchange(url("/api/v1/imports/" + id + "/error"),
                HttpMethod.POST,
                new HttpEntity<>("{\"errorMessage\":\"Something broke\"}", auth(tokenA)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
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

    @Test
    void user_isolation_cannot_start_other_users_job() {
        Long id = createJobId("AO3", "HISTORY", tokenA);

        ResponseEntity<Map> resp = transition(id, "start", tokenB);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void user_isolation_cannot_pause_other_users_job() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);

        ResponseEntity<Map> resp = transition(id, "pause", tokenB);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void user_isolation_cannot_cancel_other_users_job() {
        Long id = createJobId("AO3", "HISTORY", tokenA);

        ResponseEntity<Map> resp = transition(id, "cancel", tokenB);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── History Batch ─────────────────────────────────────────────────────────

    private ResponseEntity<Map> historyBatch(Long jobId, String body, String token) {
        return rest.exchange(url("/api/v1/imports/" + jobId + "/history-batch"),
                HttpMethod.POST, new HttpEntity<>(body, auth(token)), Map.class);
    }

    @Test
    void history_batch_upserts_stories_and_updates_progress() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);
        long nano = System.nanoTime();

        String body = """
                {"currentPage":1,"totalPages":20,"entries":[
                  {"story":{"title":"HP Fic","author":"JKR","fandom":"Harry Potter",
                   "platform":"AO3","sourceWorkId":"hist-%d"},"historyAccessDate":"2024-03-10"}
                ]}""".formatted(nano);

        ResponseEntity<Map> resp = historyBatch(id, body, tokenA);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> d = data(resp);
        assertThat(d.get("currentPage")).isEqualTo(1);
        assertThat(((Number) d.get("totalPages")).intValue()).isEqualTo(20);
        assertThat(((Number) d.get("itemsProcessed")).intValue()).isEqualTo(1);
        assertThat(d.get("status")).isEqualTo("RUNNING");

        // Story was created
        List<?> stories = (List<?>) rest.exchange(url("/api/v1/stories"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        long count = stories.stream()
                .filter(s -> {
                    Object wid = ((Map<?, ?>) s).get("sourceWorkId");
                    return ("hist-" + nano).equals(wid != null ? wid.toString() : null);
                }).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void history_batch_creates_reading_history_with_historical_date() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);
        long nano = System.nanoTime();

        String body = """
                {"currentPage":1,"totalPages":5,"entries":[
                  {"story":{"title":"Historical Fic","author":"Author","fandom":"Fandom",
                   "platform":"AO3","sourceWorkId":"hist-date-%d"},"historyAccessDate":"2023-06-15"}
                ]}""".formatted(nano);

        historyBatch(id, body, tokenA);

        // Get the story ID
        List<?> stories = (List<?>) rest.exchange(url("/api/v1/stories"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        Map<?, ?> theStory = (Map<?, ?>) stories.stream()
                .filter(s -> {
                    Object wid = ((Map<?, ?>) s).get("sourceWorkId");
                    return ("hist-date-" + nano).equals(wid != null ? wid.toString() : null);
                }).findFirst().orElseThrow();
        Long storyId = ((Number) theStory.get("id")).longValue();

        // Verify the reading history entry has the historical date
        ResponseEntity<Map> histResp = rest.exchange(
                url("/api/v1/stories/" + storyId + "/access"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class);
        List<?> history = (List<?>) histResp.getBody().get("data");
        assertThat(history).hasSize(1);
        String accessedAt = ((Map<?, ?>) history.get(0)).get("accessedAt").toString();
        assertThat(accessedAt).startsWith("2023-06-15");
        assertThat(((Map<?, ?>) history.get(0)).get("eventType")).isEqualTo("AO3_IMPORT");
    }

    @Test
    void history_batch_deduplicates_same_date_for_same_story() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);
        long nano = System.nanoTime();

        String entry = """
                {"story":{"title":"Dedup Fic","author":"Author","fandom":"Fandom",
                 "platform":"AO3","sourceWorkId":"hist-dedup-%d"},"historyAccessDate":"2024-01-20"}
                """.formatted(nano);
        String body = "{\"currentPage\":1,\"totalPages\":5,\"entries\":[" + entry + "]}";

        historyBatch(id, body, tokenA);
        historyBatch(id, body, tokenA); // send same entry again

        List<?> stories = (List<?>) rest.exchange(url("/api/v1/stories"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        Map<?, ?> theStory = (Map<?, ?>) stories.stream()
                .filter(s -> {
                    Object wid = ((Map<?, ?>) s).get("sourceWorkId");
                    return ("hist-dedup-" + nano).equals(wid != null ? wid.toString() : null);
                }).findFirst().orElseThrow();
        Long storyId = ((Number) theStory.get("id")).longValue();

        List<?> history = (List<?>) rest.exchange(
                url("/api/v1/stories/" + storyId + "/access"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        // Only one AO3_IMPORT entry for that date, despite submitting twice
        long importCount = history.stream()
                .filter(h -> "AO3_IMPORT".equals(((Map<?, ?>) h).get("eventType")))
                .count();
        assertThat(importCount).isEqualTo(1);
    }

    @Test
    void history_batch_preserves_existing_personal_notes() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);
        long nano = System.nanoTime();

        // First batch — creates the story
        String entry = """
                {"story":{"title":"Notes Fic","author":"Auth","fandom":"Fandom",
                 "platform":"AO3","sourceWorkId":"hist-note-%d"},"historyAccessDate":"2024-02-01"}
                """.formatted(nano);
        historyBatch(id, "{\"currentPage\":1,\"totalPages\":5,\"entries\":[" + entry + "]}", tokenA);

        // Add personal note to the story
        List<?> stories = (List<?>) rest.exchange(url("/api/v1/stories"),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class).getBody().get("data");
        Long storyId = ((Number) ((Map<?, ?>) stories.stream()
                .filter(s -> {
                    Object wid = ((Map<?, ?>) s).get("sourceWorkId");
                    return ("hist-note-" + nano).equals(wid != null ? wid.toString() : null);
                }).findFirst().orElseThrow()).get("id")).longValue();

        rest.exchange(url("/api/v1/stories/" + storyId + "/note"),
                HttpMethod.PUT,
                new HttpEntity<>("{\"content\":\"my private note\"}", auth(tokenA)),
                Map.class);

        // Re-import the same story — notes must survive
        historyBatch(id, "{\"currentPage\":2,\"totalPages\":5,\"entries\":[" + entry + "]}", tokenA);

        ResponseEntity<Map> getResp = rest.exchange(url("/api/v1/stories/" + storyId),
                HttpMethod.GET, new HttpEntity<>(auth(tokenA)), Map.class);
        assertThat(((Map<?, ?>) getResp.getBody().get("data")).get("personalNotes"))
                .isEqualTo("my private note");
    }

    @Test
    void history_batch_tracks_error_without_aborting_job() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);
        long nano = System.nanoTime();

        // One valid story + one null-title story (will fail validation in upsert)
        String body = """
                {"currentPage":1,"totalPages":5,"entries":[
                  {"story":{"title":"Good Fic","author":"Auth","fandom":"Fandom",
                   "platform":"AO3","sourceWorkId":"hist-ok-%d"}},
                  {"story":{"title":null,"author":null,"fandom":null,
                   "platform":"AO3","sourceWorkId":"hist-bad-%d"}}
                ]}""".formatted(nano, nano);

        ResponseEntity<Map> resp = historyBatch(id, body, tokenA);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> d = data(resp);
        // Job stays RUNNING
        assertThat(d.get("status")).isEqualTo("RUNNING");
        // errorCount incremented for the bad entry
        assertThat(((Number) d.get("errorCount")).intValue()).isGreaterThanOrEqualTo(1);
        // Good story was still processed
        assertThat(((Number) d.get("itemsProcessed")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void history_batch_requires_running_status() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        // Job is PENDING — not started

        String body = """
                {"currentPage":1,"totalPages":5,"entries":[
                  {"story":{"title":"A Fic","author":"Auth","fandom":"Fandom","platform":"AO3","sourceWorkId":"x"}}
                ]}""";

        ResponseEntity<Map> resp = historyBatch(id, body, tokenA);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void history_batch_user_isolation() {
        Long idA = createJobId("AO3", "HISTORY", tokenA);
        transition(idA, "start", tokenA);

        String body = """
                {"currentPage":1,"totalPages":5,"entries":[
                  {"story":{"title":"A Fic","author":"Auth","fandom":"Fandom","platform":"AO3","sourceWorkId":"iso-x"}}
                ]}""";

        // User B cannot post to user A's job
        ResponseEntity<Map> resp = historyBatch(idA, body, tokenB);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void history_batch_accumulates_items_processed_across_pages() {
        Long id = createJobId("AO3", "HISTORY", tokenA);
        transition(id, "start", tokenA);
        long nano = System.nanoTime();

        String page1 = """
                {"currentPage":1,"totalPages":3,"entries":[
                  {"story":{"title":"Fic A","author":"Auth","fandom":"F","platform":"AO3","sourceWorkId":"acc-a-%d"}},
                  {"story":{"title":"Fic B","author":"Auth","fandom":"F","platform":"AO3","sourceWorkId":"acc-b-%d"}}
                ]}""".formatted(nano, nano);
        String page2 = """
                {"currentPage":2,"totalPages":3,"entries":[
                  {"story":{"title":"Fic C","author":"Auth","fandom":"F","platform":"AO3","sourceWorkId":"acc-c-%d"}}
                ]}""".formatted(nano);

        historyBatch(id, page1, tokenA);
        ResponseEntity<Map> resp2 = historyBatch(id, page2, tokenA);

        assertThat(((Number) data(resp2).get("itemsProcessed")).intValue()).isEqualTo(3);
        assertThat(data(resp2).get("currentPage")).isEqualTo(2);
    }
}
