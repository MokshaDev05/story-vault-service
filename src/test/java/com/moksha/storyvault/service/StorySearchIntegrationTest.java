package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.PagedApiResponse;
import com.moksha.storyvault.dto.StoryRequest;
import com.moksha.storyvault.dto.StoryResponse;
import com.moksha.storyvault.dto.StorySearchRequest;
import com.moksha.storyvault.dto.StorySearchRequest.SortField;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.Tag;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.KudosStatus;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.ReadingStatus;
import com.moksha.storyvault.model.enums.StoryStatus;
import com.moksha.storyvault.model.Label;
import com.moksha.storyvault.repository.LabelRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.repository.TagRepository;
import com.moksha.storyvault.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class StorySearchIntegrationTest {

    @Autowired StoryService storyService;
    @Autowired StoryRepository storyRepository;
    @Autowired TagRepository tagRepository;
    @Autowired UserRepository userRepository;
    @Autowired LabelRepository labelRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .username("search-tester")
                .password("x")
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Story save(String title, String author, String fandom,
                       List<String> relationships, Set<String> tagNames) {
        Set<Tag> tags = new HashSet<>();
        for (String name : tagNames) {
            tags.add(tagRepository.findByName(name)
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(name).build())));
        }
        Story s = Story.builder()
                .title(title).author(author).fandom(fandom)
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .user(user)
                .build();
        s.setRelationships(new ArrayList<>(relationships));
        s.setTags(tags);
        return storyRepository.saveAndFlush(s);
    }

    private List<StoryResponse> search(StorySearchRequest req) {
        return storyService.advancedSearch(req, 0, 1000).getData();
    }

    // ── Filter by author ──────────────────────────────────────────────────────

    @Test
    void search_filters_by_authorContains() {
        save("Story A", "Alice Writer", "Fandom X", List.of(), Set.of());
        save("Story B", "Bob Scribe",   "Fandom X", List.of(), Set.of());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().authorContains("alice").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A");
    }

    @Test
    void search_author_filter_is_substring_match() {
        save("Story A", "Alice Writer", "Fandom X", List.of(), Set.of());
        save("Story B", "Bob Scribe",   "Fandom X", List.of(), Set.of());
        save("Story C", "Charlie Alice","Fandom X", List.of(), Set.of());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().authorContains("alice").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A", "Story C");
    }

    // ── Filter by fandom ──────────────────────────────────────────────────────

    @Test
    void search_filters_by_fandomContains() {
        save("Story A", "Author", "Dragon Age",  List.of(), Set.of());
        save("Story B", "Author", "Mass Effect", List.of(), Set.of());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().fandomContains("dragon").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A");
    }

    // ── Filter by relationship ────────────────────────────────────────────────

    @Test
    void search_filters_by_relationshipContains() {
        save("Story A", "Author", "Fandom X", List.of("James/Lily"),    Set.of());
        save("Story B", "Author", "Fandom X", List.of("Harry/Hermione"), Set.of());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().relationshipContains("james").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A");
    }

    @Test
    void search_excludes_unmatched_relationship() {
        save("Story A", "Author", "Fandom X", List.of("James/Lily"), Set.of());
        save("Story B", "Author", "Fandom X", List.of(),             Set.of());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().relationshipContains("james").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .doesNotContain("Story B");
    }

    // ── Filter by freeform tag ────────────────────────────────────────────────

    @Test
    void search_filters_by_tagContains() {
        save("Story A", "Author", "Fandom X", List.of(), Set.of("slow burn"));
        save("Story B", "Author", "Fandom X", List.of(), Set.of("hurt/comfort"));

        List<StoryResponse> results = search(
                StorySearchRequest.builder().tagContains("slow").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A");
    }

    @Test
    void search_tag_filter_is_case_insensitive() {
        save("Story A", "Author", "Fandom X", List.of(), Set.of("slow burn"));
        save("Story B", "Author", "Fandom X", List.of(), Set.of("hurt/comfort"));

        List<StoryResponse> results = search(
                StorySearchRequest.builder().tagContains("SLOW").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A");
    }

    // ── Filter by personal notes ──────────────────────────────────────────────

    @Test
    void search_filters_by_noteContains() {
        storyRepository.saveAndFlush(Story.builder()
                .title("Noted Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .personalNotes("this one made me cry").user(user).build());
        storyRepository.saveAndFlush(Story.builder()
                .title("No Note Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .user(user).build());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().noteContains("cry").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Noted Story");
    }

    @Test
    void search_noteContains_is_case_insensitive() {
        storyRepository.saveAndFlush(Story.builder()
                .title("Noted Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .personalNotes("this one made me CRY").user(user).build());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().noteContains("cry").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Noted Story");
    }

    // ── Filter by label ───────────────────────────────────────────────────────

    @Test
    void search_filters_by_labelId() {
        Label faveLabel = labelRepository.saveAndFlush(
                Label.builder().name("Favourites").user(user).build());

        Story labelled = storyRepository.saveAndFlush(Story.builder()
                .title("Labelled Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .user(user).build());
        labelled.getLabels().add(faveLabel);
        storyRepository.saveAndFlush(labelled);

        storyRepository.saveAndFlush(Story.builder()
                .title("Unlabelled Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .user(user).build());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().labelId(faveLabel.getId()).build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Labelled Story");
    }

    // ── User isolation ────────────────────────────────────────────────────────

    @Test
    void search_is_user_scoped_and_never_returns_other_users_stories() {
        save("My Story", "Author", "Fandom X", List.of(), Set.of());

        User otherUser = userRepository.save(User.builder()
                .username("search-other-" + System.nanoTime())
                .password("x").build());
        storyRepository.saveAndFlush(Story.builder()
                .title("Other User Story").author("Author").fandom("Fandom X")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .user(otherUser).build());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("My Story");
    }

    // ── No filter returns all ─────────────────────────────────────────────────

    @Test
    void search_with_no_filters_returns_all_user_stories() {
        save("Story A", "Author A", "Fandom 1", List.of(), Set.of());
        save("Story B", "Author B", "Fandom 2", List.of(), Set.of());
        save("Story C", "Author C", "Fandom 3", List.of(), Set.of());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().build());

        assertThat(results).hasSize(3);
    }

    // ── Kudos filter ──────────────────────────────────────────────────────────

    @Test
    void search_kudosGiven_true_returns_only_given_stories() {
        storyRepository.saveAndFlush(Story.builder()
                .title("Kudosed").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .kudosStatus(KudosStatus.GIVEN).user(user).build());
        storyRepository.saveAndFlush(Story.builder()
                .title("Not Kudosed").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .kudosStatus(KudosStatus.UNKNOWN).user(user).build());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().kudosGiven(true).build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Kudosed");
    }

    @Test
    void search_kudosGiven_false_excludes_given_stories() {
        storyRepository.saveAndFlush(Story.builder()
                .title("Kudosed").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .kudosStatus(KudosStatus.GIVEN).user(user).build());
        storyRepository.saveAndFlush(Story.builder()
                .title("Not Detected").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .kudosStatus(KudosStatus.NOT_DETECTED).user(user).build());
        storyRepository.saveAndFlush(Story.builder()
                .title("Unknown").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .kudosStatus(KudosStatus.UNKNOWN).user(user).build());

        List<StoryResponse> results = search(
                StorySearchRequest.builder().kudosGiven(false).build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Not Detected", "Unknown")
                .doesNotContain("Kudosed");
    }

    // ── Pagination ────────────────────────────────────────────────────────────

    @Test
    void search_pagination_returns_correct_page_size() {
        for (int i = 1; i <= 5; i++) {
            save("Story " + i, "Author", "Fandom", List.of(), Set.of());
        }

        PagedApiResponse<StoryResponse> page0 =
                storyService.advancedSearch(StorySearchRequest.builder().build(), 0, 2);
        PagedApiResponse<StoryResponse> page1 =
                storyService.advancedSearch(StorySearchRequest.builder().build(), 1, 2);

        assertThat(page0.getData()).hasSize(2);
        assertThat(page1.getData()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
    }

    @Test
    void search_pagination_out_of_bounds_returns_empty() {
        save("Story A", "Author", "Fandom", List.of(), Set.of());
        save("Story B", "Author", "Fandom", List.of(), Set.of());

        PagedApiResponse<StoryResponse> result =
                storyService.advancedSearch(StorySearchRequest.builder().build(), 99, 10);

        assertThat(result.getData()).isEmpty();
    }

    // ── Summary and tags in response ──────────────────────────────────────────

    @Test
    void search_results_include_summary() {
        storyRepository.saveAndFlush(Story.builder()
                .title("Story With Summary").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .summary("A detailed summary of this story.").user(user).build());

        List<StoryResponse> results = search(StorySearchRequest.builder().build());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSummary()).isEqualTo("A detailed summary of this story.");
    }

    @Test
    void search_results_include_tags() {
        save("Tagged Story", "Author", "Fandom", List.of(), Set.of("slow burn", "found family"));

        List<StoryResponse> results = search(
                StorySearchRequest.builder().titleContains("Tagged Story").build());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTags()).containsExactlyInAnyOrder("slow burn", "found family");
    }

    // ── Sort ──────────────────────────────────────────────────────────────────

    @Test
    void search_sort_by_title_ascending_is_stable_across_pages() {
        save("Zebra", "Author", "Fandom", List.of(), Set.of());
        save("Apple", "Author", "Fandom", List.of(), Set.of());
        save("Mango", "Author", "Fandom", List.of(), Set.of());

        StorySearchRequest req = StorySearchRequest.builder()
                .sortBy(StorySearchRequest.SortField.TITLE).sortDir("asc").build();

        PagedApiResponse<StoryResponse> page0 = storyService.advancedSearch(req, 0, 2);
        PagedApiResponse<StoryResponse> page1 = storyService.advancedSearch(req, 1, 2);

        assertThat(page0.getData()).extracting(StoryResponse::getTitle)
                .containsExactly("Apple", "Mango");
        assertThat(page1.getData()).extracting(StoryResponse::getTitle)
                .containsExactly("Zebra");
    }

    // ── Recency sort ──────────────────────────────────────────────────────────

    private Story saveWithLastAccessed(String title, LocalDateTime lastAccessed) {
        return storyRepository.saveAndFlush(Story.builder()
                .title(title).author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .lastAccessedAt(lastAccessed).user(user).build());
    }

    @Test
    void recencySort_recentlyRead_orders_newest_first_never_read_last() {
        LocalDateTime old    = LocalDateTime.of(2024, 1, 1, 12, 0);
        LocalDateTime recent = LocalDateTime.of(2025, 6, 1, 12, 0);
        saveWithLastAccessed("Old Read",    old);
        saveWithLastAccessed("Recent Read", recent);
        saveWithLastAccessed("Never Read",  null);

        List<StoryResponse> results = search(
                StorySearchRequest.builder().sortBy(SortField.RECENTLY_READ).build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactly("Recent Read", "Old Read", "Never Read");
    }

    @Test
    void recencySort_longestAgoRead_orders_oldest_first_never_read_last() {
        LocalDateTime old    = LocalDateTime.of(2024, 1, 1, 12, 0);
        LocalDateTime recent = LocalDateTime.of(2025, 6, 1, 12, 0);
        saveWithLastAccessed("Old Read",    old);
        saveWithLastAccessed("Recent Read", recent);
        saveWithLastAccessed("Never Read",  null);

        List<StoryResponse> results = search(
                StorySearchRequest.builder().sortBy(SortField.LONGEST_AGO_READ).build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactly("Old Read", "Recent Read", "Never Read");
    }

    @Test
    void recencySort_neverReadFirst_shows_null_before_non_null() {
        LocalDateTime old    = LocalDateTime.of(2024, 1, 1, 12, 0);
        LocalDateTime recent = LocalDateTime.of(2025, 6, 1, 12, 0);
        saveWithLastAccessed("Old Read",    old);
        saveWithLastAccessed("Recent Read", recent);
        saveWithLastAccessed("Never Read",  null);

        List<StoryResponse> results = search(
                StorySearchRequest.builder().sortBy(SortField.NEVER_READ_FIRST).build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactly("Never Read", "Old Read", "Recent Read");
    }

    @Test
    void recencySort_equal_dates_sort_stably_by_title() {
        LocalDateTime same = LocalDateTime.of(2025, 3, 15, 10, 0);
        saveWithLastAccessed("Zebra", same);
        saveWithLastAccessed("Apple", same);
        saveWithLastAccessed("Mango", same);

        List<StoryResponse> results = search(
                StorySearchRequest.builder().sortBy(SortField.RECENTLY_READ).build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactly("Apple", "Mango", "Zebra");
    }

    @Test
    void recencySort_stable_across_pages() {
        LocalDateTime t1 = LocalDateTime.of(2025, 5, 1, 12, 0);
        LocalDateTime t2 = LocalDateTime.of(2025, 4, 1, 12, 0);
        LocalDateTime t3 = LocalDateTime.of(2025, 3, 1, 12, 0);
        saveWithLastAccessed("P-2025-05", t1);
        saveWithLastAccessed("P-2025-04", t2);
        saveWithLastAccessed("P-2025-03", t3);
        saveWithLastAccessed("Q-Never-A", null);
        saveWithLastAccessed("Q-Never-B", null);

        StorySearchRequest req = StorySearchRequest.builder().sortBy(SortField.RECENTLY_READ).build();
        PagedApiResponse<StoryResponse> page0 = storyService.advancedSearch(req, 0, 3);
        PagedApiResponse<StoryResponse> page1 = storyService.advancedSearch(req, 1, 3);

        assertThat(page0.getData()).extracting(StoryResponse::getTitle)
                .containsExactly("P-2025-05", "P-2025-04", "P-2025-03");
        assertThat(page1.getData()).extracting(StoryResponse::getTitle)
                .containsExactly("Q-Never-A", "Q-Never-B");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private StoryRequest minimalRequest(String title) {
        return StoryRequest.builder()
                .title(title)
                .author("Test Author")
                .fandom("Test Fandom")
                .platform(Platform.AO3)
                .status(StoryStatus.ONGOING)
                .rating(Rating.NOT_RATED)
                .build();
    }

    // ── New UI polish tests ───────────────────────────────────────────────────

    @Test
    void summary_isIncludedInStoryResponse() {
        StoryRequest req = minimalRequest("Test Story");
        req.setSummary("A test summary for this work.");
        Long id = storyService.create(req).getId();

        StorySearchRequest search = new StorySearchRequest();
        search.setSortBy(StorySearchRequest.SortField.TITLE);
        PagedApiResponse<StoryResponse> results = storyService.advancedSearch(search, 0, 20);

        StoryResponse found = results.getData().stream()
                .filter(s -> s.getId().equals(id)).findFirst().orElseThrow();
        assertThat(found.getSummary()).isEqualTo("A test summary for this work.");
    }

    @Test
    void lastAccessedAt_isSetOnReUpsert() {
        // lastAccessedAt is set on the merge path (re-upsert of an already-known story).
        StoryRequest req = minimalRequest("AccessTimeTest");
        storyService.upsert(req); // first upsert → creates the story (lastAccessedAt not set yet)
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        StoryResponse revisited = storyService.upsert(req).story(); // second → merges, sets lastAccessedAt
        LocalDateTime after  = LocalDateTime.now().plusSeconds(1);

        assertThat(revisited.getLastAccessedAt()).isNotNull();
        assertThat(revisited.getLastAccessedAt()).isAfter(before);
        assertThat(revisited.getLastAccessedAt()).isBefore(after);
    }

    @Test
    void readingStatus_isIncludedInStoryResponse() {
        StoryRequest req = minimalRequest("ReadingStatusTest");
        req.setReadingStatus(ReadingStatus.STILL_READING);
        Long id = storyService.create(req).getId();

        StorySearchRequest search = new StorySearchRequest();
        PagedApiResponse<StoryResponse> results = storyService.advancedSearch(search, 0, 20);

        StoryResponse found = results.getData().stream()
                .filter(s -> s.getId().equals(id)).findFirst().orElseThrow();
        assertThat(found.getReadingStatus()).isEqualTo(ReadingStatus.STILL_READING);
    }

    @Test
    void storyStatus_isIncludedInStoryResponse() {
        StoryRequest req = minimalRequest("StatusTest");
        req.setStatus(StoryStatus.COMPLETE);
        Long id = storyService.create(req).getId();

        StorySearchRequest search = new StorySearchRequest();
        PagedApiResponse<StoryResponse> results = storyService.advancedSearch(search, 0, 20);

        StoryResponse found = results.getData().stream()
                .filter(s -> s.getId().equals(id)).findFirst().orElseThrow();
        assertThat(found.getStatus()).isEqualTo(StoryStatus.COMPLETE);
    }

    // ── Card layout API contract tests ──────────────────────────────────────────

    @Test
    void lastAccessedAt_isNullAfterCreate_so_frontend_shows_Never() {
        // Creating a story is not the same as reading it.
        // lastAccessedAt must remain null until the user genuinely opens the work.
        StoryRequest req = minimalRequest("LastAccessedDisplayTest");
        Long id = storyService.create(req).getId();

        StorySearchRequest search = new StorySearchRequest();
        StoryResponse found = storyService.advancedSearch(search, 0, 20).getData().stream()
                .filter(s -> s.getId().equals(id)).findFirst().orElseThrow();

        // Field is present in the response (as null) — frontend renders "Last accessed: Never"
        assertThat(found.getLastAccessedAt()).isNull();
    }

    @Test
    void lastAccessedAt_null_in_db_returns_null_in_response_for_Never_display() {
        // Insert a story directly with no lastAccessedAt to verify null passes through
        Story bare = storyRepository.save(Story.builder()
                .title("NullAccessTest")
                .author("Author")
                .fandom("Fandom")
                .platform(Platform.AO3)
                .user(user)
                .build());

        StorySearchRequest search = new StorySearchRequest();
        StoryResponse found = storyService.advancedSearch(search, 0, 20).getData().stream()
                .filter(s -> s.getId().equals(bare.getId())).findFirst().orElseThrow();

        // Null passes through — frontend renders "Last accessed: Never"
        assertThat(found.getLastAccessedAt()).isNull();
    }

    @Test
    void storyStatus_is_from_stored_value_not_inferred() {
        // ONGOING status must come from the field, not inferred from chapter count
        StoryRequest req = minimalRequest("InferenceTest");
        req.setStatus(StoryStatus.ONGOING);
        req.setChapterCount(1);
        req.setTotalChapters(1); // equal chapters — don't infer COMPLETE
        Long id = storyService.create(req).getId();

        StorySearchRequest search = new StorySearchRequest();
        StoryResponse found = storyService.advancedSearch(search, 0, 20).getData().stream()
                .filter(s -> s.getId().equals(id)).findFirst().orElseThrow();

        assertThat(found.getStatus()).isEqualTo(StoryStatus.ONGOING);
    }

    @Test
    void tags_relationships_categories_warnings_all_returned_in_response() {
        // Verify all four tag-type fields are present in the response
        StoryRequest req = minimalRequest("AllTagTypesTest");
        req.setTags(Set.of("slow burn", "angst"));
        req.setRelationships(List.of("Character A/Character B"));
        req.setCategories(List.of("M/M"));
        req.setArchiveWarnings(List.of("Creator Chose Not To Use Archive Warnings"));
        Long id = storyService.create(req).getId();

        StorySearchRequest search = new StorySearchRequest();
        StoryResponse found = storyService.advancedSearch(search, 0, 20).getData().stream()
                .filter(s -> s.getId().equals(id)).findFirst().orElseThrow();

        assertThat(found.getTags()).contains("slow burn", "angst");
        assertThat(found.getRelationships()).contains("Character A/Character B");
        assertThat(found.getCategories()).contains("M/M");
        assertThat(found.getArchiveWarnings()).contains("Creator Chose Not To Use Archive Warnings");
    }

    @Test
    void wordCount_and_chapterCount_returned_in_response_for_meta_row() {
        StoryRequest req = minimalRequest("MetaRowTest");
        req.setWordCount(87_450);
        req.setChapterCount(42);
        Long id = storyService.create(req).getId();

        StorySearchRequest search = new StorySearchRequest();
        StoryResponse found = storyService.advancedSearch(search, 0, 20).getData().stream()
                .filter(s -> s.getId().equals(id)).findFirst().orElseThrow();

        assertThat(found.getWordCount()).isEqualTo(87_450);
        assertThat(found.getChapterCount()).isEqualTo(42);
    }

    // ── Word count fallback tests ─────────────────────────────────────────────

    @Test
    void wordCount_null_is_returned_for_Unknown_display() {
        StoryRequest req = minimalRequest("WordCountUnknownTest");
        // No wordCount set — should be null
        Long id = storyService.create(req).getId();
        StorySearchRequest search = new StorySearchRequest();
        StoryResponse found = storyService.advancedSearch(search, 0, 20).getData().stream()
                .filter(s -> s.getId().equals(id)).findFirst().orElseThrow();
        assertThat(found.getWordCount()).isNull();
    }

    @Test
    void chapterCount_null_is_returned_for_Unknown_display() {
        StoryRequest req = minimalRequest("ChapterCountUnknownTest");
        Long id = storyService.create(req).getId();
        StorySearchRequest search = new StorySearchRequest();
        StoryResponse found = storyService.advancedSearch(search, 0, 20).getData().stream()
                .filter(s -> s.getId().equals(id)).findFirst().orElseThrow();
        assertThat(found.getChapterCount()).isNull();
    }
}
