package com.moksha.storyvault.controller;

import com.moksha.storyvault.model.User;
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
class StatsControllerTest {

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
                .username("stats-a-" + nano)
                .password(passwordEncoder.encode("pass"))
                .build());
        userB = userRepository.save(User.builder()
                .username("stats-b-" + nano)
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
        shelfRepository.findAllWithStoriesByUser(user).forEach(shelfRepository::delete);
        storyRepository.findAllWithTagsByUser(user).forEach(storyRepository::delete);
        userRepository.delete(user);
    }

    private String url(String path) { return "http://localhost:" + port + path; }

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private Long upsert(String title, String author, String fandom, String workId,
                        Integer wordCount, String status, String readingStatus, String kudosStatus,
                        List<String> tags, List<String> relationships, String token) {
        String tagsJson   = tags          == null ? "[]" : "[" + String.join(",", tags.stream().map(t -> "\"" + t + "\"").toList()) + "]";
        String relsJson   = relationships == null ? "[]" : "[" + String.join(",", relationships.stream().map(r -> "\"" + r + "\"").toList()) + "]";
        String wcJson     = wordCount     == null ? "null" : wordCount.toString();
        String statusJson = status        == null ? "null" : "\"" + status + "\"";
        String rsJson     = readingStatus == null ? "null" : "\"" + readingStatus + "\"";
        String ksJson     = kudosStatus   == null ? "null" : "\"" + kudosStatus + "\"";
        String body = """
                {"title":"%s","author":"%s","fandom":"%s",
                 "platform":"AO3","sourceWorkId":"%s",
                 "originalUrl":"https://archiveofourown.org/works/%s",
                 "wordCount":%s,"status":%s,"readingStatus":%s,
                 "kudosStatus":%s,"tags":%s,"relationships":%s}
                """.formatted(title, author, fandom, workId, workId,
                wcJson, statusJson, rsJson, ksJson, tagsJson, relsJson);
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stories/upsert"), HttpMethod.POST,
                new HttpEntity<>(body, auth(token)), Map.class);
        assertThat(resp.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.OK);
        return Long.valueOf(((Map<?, ?>) resp.getBody().get("data")).get("id").toString());
    }

    private void logAccess(Long storyId, int chapterNum, String token) {
        rest.exchange(url("/api/v1/stories/" + storyId + "/access"), HttpMethod.POST,
                new HttpEntity<>("{\"chapterNumber\":" + chapterNum + "}", auth(token)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stats(String token) {
        ResponseEntity<Map> resp = rest.exchange(
                url("/api/v1/stats"), HttpMethod.GET,
                new HttpEntity<>(auth(token)), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) resp.getBody().get("data");
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    void empty_state_returns_zeros_and_empty_lists() {
        Map<String, Object> data = stats(tokenA);
        assertThat(((Number) data.get("totalStories")).longValue()).isZero();
        assertThat(((Number) data.get("totalWordsArchived")).longValue()).isZero();
        assertThat(((Number) data.get("kudosedCount")).longValue()).isZero();
        assertThat(((Number) data.get("collectionsCount")).longValue()).isZero();
        assertThat(((Number) data.get("connectedAccountsCount")).longValue()).isZero();
        assertThat((List<?>) data.get("topFandoms")).isEmpty();
        assertThat((List<?>) data.get("topAuthors")).isEmpty();
        assertThat((List<?>) data.get("mostAccessedStories")).isEmpty();
        assertThat((List<?>) data.get("recentlyAccessedStories")).isEmpty();
    }

    // ── Total story count & word count ────────────────────────────────────────

    @Test
    void total_story_count_and_words() {
        long n = System.nanoTime();
        upsert("T1", "A", "Fandom", "wc1-" + n, 1000, null, null, null, null, null, tokenA);
        upsert("T2", "A", "Fandom", "wc2-" + n, 2500, null, null, null, null, null, tokenA);
        upsert("T3", "A", "Fandom", "wc3-" + n, null, null, null, null, null, null, tokenA);

        Map<String, Object> data = stats(tokenA);
        assertThat(((Number) data.get("totalStories")).longValue()).isEqualTo(3);
        assertThat(((Number) data.get("totalWordsArchived")).longValue()).isEqualTo(3500);
    }

    // ── Story status counts ───────────────────────────────────────────────────

    @Test
    void story_status_counts() {
        long n = System.nanoTime();
        upsert("S1", "A", "F", "ss1-" + n, null, "COMPLETE", null, null, null, null, tokenA);
        upsert("S2", "A", "F", "ss2-" + n, null, "COMPLETE", null, null, null, null, tokenA);
        upsert("S3", "A", "F", "ss3-" + n, null, "ONGOING",  null, null, null, null, tokenA);

        Map<?, ?> byStatus = (Map<?, ?>) stats(tokenA).get("byStoryStatus");
        assertThat(((Number) byStatus.get("COMPLETE")).longValue()).isEqualTo(2);
        assertThat(((Number) byStatus.get("ONGOING")).longValue()).isEqualTo(1);
    }

    // ── Reading status counts ─────────────────────────────────────────────────

    @Test
    void reading_status_counts() {
        long n = System.nanoTime();
        upsert("R1", "A", "F", "rs1-" + n, null, null, "STILL_READING", null, null, null, tokenA);
        upsert("R2", "A", "F", "rs2-" + n, null, null, "STILL_READING", null, null, null, tokenA);
        upsert("R3", "A", "F", "rs3-" + n, null, null, "FINISHED_READING", null, null, null, tokenA);

        Map<?, ?> byReading = (Map<?, ?>) stats(tokenA).get("byReadingStatus");
        assertThat(((Number) byReading.get("STILL_READING")).longValue()).isEqualTo(2);
        assertThat(((Number) byReading.get("FINISHED_READING")).longValue()).isEqualTo(1);
    }

    // ── Kudos count ───────────────────────────────────────────────────────────

    @Test
    void kudosed_count() {
        long n = System.nanoTime();
        upsert("K1", "A", "F", "kd1-" + n, null, null, null, "GIVEN",        null, null, tokenA);
        upsert("K2", "A", "F", "kd2-" + n, null, null, null, "GIVEN",        null, null, tokenA);
        upsert("K3", "A", "F", "kd3-" + n, null, null, null, "NOT_DETECTED", null, null, tokenA);

        assertThat(((Number) stats(tokenA).get("kudosedCount")).longValue()).isEqualTo(2);
    }

    // ── Top fandoms ───────────────────────────────────────────────────────────

    @Test
    void top_fandoms_ordered_by_count() {
        long n = System.nanoTime();
        upsert("F1", "A", "FandomA", "tf1-" + n, null, null, null, null, null, null, tokenA);
        upsert("F2", "A", "FandomA", "tf2-" + n, null, null, null, null, null, null, tokenA);
        upsert("F3", "A", "FandomA", "tf3-" + n, null, null, null, null, null, null, tokenA);
        upsert("F4", "A", "FandomB", "tf4-" + n, null, null, null, null, null, null, tokenA);
        upsert("F5", "A", "FandomB", "tf5-" + n, null, null, null, null, null, null, tokenA);

        List<Map<?, ?>> fandoms = (List<Map<?, ?>>) stats(tokenA).get("topFandoms");
        assertThat(fandoms).isNotEmpty();
        Map<?, ?> top = fandoms.get(0);
        assertThat(top.get("label")).isEqualTo("FandomA");
        assertThat(((Number) top.get("count")).longValue()).isEqualTo(3);
    }

    // ── Top authors ───────────────────────────────────────────────────────────

    @Test
    void top_authors_ordered_by_count() {
        long n = System.nanoTime();
        upsert("A1", "AuthorX", "F", "ta1-" + n, null, null, null, null, null, null, tokenA);
        upsert("A2", "AuthorX", "F", "ta2-" + n, null, null, null, null, null, null, tokenA);
        upsert("A3", "AuthorY", "F", "ta3-" + n, null, null, null, null, null, null, tokenA);

        List<Map<?, ?>> authors = (List<Map<?, ?>>) stats(tokenA).get("topAuthors");
        assertThat(authors.get(0).get("label")).isEqualTo("AuthorX");
        assertThat(((Number) authors.get(0).get("count")).longValue()).isEqualTo(2);
    }

    // ── Top relationships ─────────────────────────────────────────────────────

    @Test
    void top_relationships_ordered_by_count() {
        long n = System.nanoTime();
        List<String> rels = List.of("Person A/Person B");
        upsert("Rel1", "A", "F", "tr1-" + n, null, null, null, null, null, rels, tokenA);
        upsert("Rel2", "A", "F", "tr2-" + n, null, null, null, null, null, rels, tokenA);
        upsert("Rel3", "A", "F", "tr3-" + n, null, null, null, null, null, List.of("Person C/Person D"), tokenA);

        List<Map<?, ?>> ships = (List<Map<?, ?>>) stats(tokenA).get("topRelationships");
        assertThat(ships).isNotEmpty();
        assertThat(ships.get(0).get("label")).isEqualTo("Person A/Person B");
        assertThat(((Number) ships.get(0).get("count")).longValue()).isEqualTo(2);
    }

    // ── Top tags ──────────────────────────────────────────────────────────────

    @Test
    void top_tags_ordered_by_count() {
        long n = System.nanoTime();
        upsert("Tag1", "A", "F", "tt1-" + n, null, null, null, null, List.of("fluff", "hurt/comfort"), null, tokenA);
        upsert("Tag2", "A", "F", "tt2-" + n, null, null, null, null, List.of("fluff"), null, tokenA);
        upsert("Tag3", "A", "F", "tt3-" + n, null, null, null, null, List.of("angst"), null, tokenA);

        List<Map<?, ?>> tagList = (List<Map<?, ?>>) stats(tokenA).get("topTags");
        assertThat(tagList).isNotEmpty();
        assertThat(tagList.get(0).get("label")).isEqualTo("fluff");
        assertThat(((Number) tagList.get(0).get("count")).longValue()).isEqualTo(2);
    }

    // ── Most accessed stories ─────────────────────────────────────────────────

    @Test
    void most_accessed_stories_ordered_by_access_count() {
        long n = System.nanoTime();
        Long s1 = upsert("Acc1", "A", "F", "ma1-" + n, null, null, null, null, null, null, tokenA);
        Long s2 = upsert("Acc2", "A", "F", "ma2-" + n, null, null, null, null, null, null, tokenA);
        logAccess(s1, 1, tokenA);
        logAccess(s1, 2, tokenA);
        logAccess(s1, 3, tokenA);
        logAccess(s2, 1, tokenA);

        List<Map<?, ?>> accessed = (List<Map<?, ?>>) stats(tokenA).get("mostAccessedStories");
        assertThat(accessed).isNotEmpty();
        assertThat(accessed.get(0).get("storyId").toString()).isEqualTo(s1.toString());
        assertThat(((Number) accessed.get(0).get("accessCount")).longValue()).isEqualTo(3);
    }

    // ── Collections count ─────────────────────────────────────────────────────

    @Test
    void collections_count() {
        rest.exchange(url("/api/v1/collections"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Col1\"}", auth(tokenA)), Map.class);
        rest.exchange(url("/api/v1/collections"), HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Col2\"}", auth(tokenA)), Map.class);

        assertThat(((Number) stats(tokenA).get("collectionsCount")).longValue()).isEqualTo(2);
    }

    // ── User isolation ────────────────────────────────────────────────────────

    @Test
    void stats_are_user_scoped() {
        long n = System.nanoTime();
        upsert("UA1", "A", "FandomA", "iso1-" + n, 100, null, null, null, null, null, tokenA);
        upsert("UA2", "A", "FandomA", "iso2-" + n, 200, null, null, null, null, null, tokenA);
        upsert("UB1", "A", "FandomB", "iso3-" + n, 999, null, null, null, null, null, tokenB);

        Map<String, Object> dataA = stats(tokenA);
        assertThat(((Number) dataA.get("totalStories")).longValue()).isEqualTo(2);
        assertThat(((Number) dataA.get("totalWordsArchived")).longValue()).isEqualTo(300);

        Map<String, Object> dataB = stats(tokenB);
        assertThat(((Number) dataB.get("totalStories")).longValue()).isEqualTo(1);
        assertThat(((Number) dataB.get("totalWordsArchived")).longValue()).isEqualTo(999);

        List<Map<?, ?>> fandomsA = (List<Map<?, ?>>) dataA.get("topFandoms");
        assertThat(fandomsA.get(0).get("label")).isEqualTo("FandomA");

        List<Map<?, ?>> fandomsB = (List<Map<?, ?>>) dataB.get("topFandoms");
        assertThat(fandomsB.get(0).get("label")).isEqualTo("FandomB");
    }
}
