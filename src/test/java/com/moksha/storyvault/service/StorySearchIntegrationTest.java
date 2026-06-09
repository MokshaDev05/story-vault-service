package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.StoryResponse;
import com.moksha.storyvault.dto.StorySearchRequest;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.Tag;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
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

    // ── Filter by author ──────────────────────────────────────────────────────

    @Test
    void search_filters_by_authorContains() {
        save("Story A", "Alice Writer", "Fandom X", List.of(), Set.of());
        save("Story B", "Bob Scribe",   "Fandom X", List.of(), Set.of());

        List<StoryResponse> results = storyService.advancedSearch(
                StorySearchRequest.builder().authorContains("alice").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A");
    }

    @Test
    void search_author_filter_is_substring_match() {
        save("Story A", "Alice Writer", "Fandom X", List.of(), Set.of());
        save("Story B", "Bob Scribe",   "Fandom X", List.of(), Set.of());
        save("Story C", "Charlie Alice","Fandom X", List.of(), Set.of());

        List<StoryResponse> results = storyService.advancedSearch(
                StorySearchRequest.builder().authorContains("alice").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A", "Story C");
    }

    // ── Filter by fandom ──────────────────────────────────────────────────────

    @Test
    void search_filters_by_fandomContains() {
        save("Story A", "Author", "Dragon Age",  List.of(), Set.of());
        save("Story B", "Author", "Mass Effect", List.of(), Set.of());

        List<StoryResponse> results = storyService.advancedSearch(
                StorySearchRequest.builder().fandomContains("dragon").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A");
    }

    // ── Filter by relationship ────────────────────────────────────────────────

    @Test
    void search_filters_by_relationshipContains() {
        save("Story A", "Author", "Fandom X", List.of("James/Lily"),    Set.of());
        save("Story B", "Author", "Fandom X", List.of("Harry/Hermione"), Set.of());

        List<StoryResponse> results = storyService.advancedSearch(
                StorySearchRequest.builder().relationshipContains("james").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A");
    }

    @Test
    void search_excludes_unmatched_relationship() {
        save("Story A", "Author", "Fandom X", List.of("James/Lily"), Set.of());
        save("Story B", "Author", "Fandom X", List.of(),             Set.of());

        List<StoryResponse> results = storyService.advancedSearch(
                StorySearchRequest.builder().relationshipContains("james").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .doesNotContain("Story B");
    }

    // ── Filter by freeform tag ────────────────────────────────────────────────

    @Test
    void search_filters_by_tagContains() {
        save("Story A", "Author", "Fandom X", List.of(), Set.of("slow burn"));
        save("Story B", "Author", "Fandom X", List.of(), Set.of("hurt/comfort"));

        List<StoryResponse> results = storyService.advancedSearch(
                StorySearchRequest.builder().tagContains("slow").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A");
    }

    @Test
    void search_tag_filter_is_case_insensitive() {
        save("Story A", "Author", "Fandom X", List.of(), Set.of("slow burn"));
        save("Story B", "Author", "Fandom X", List.of(), Set.of("hurt/comfort"));

        List<StoryResponse> results = storyService.advancedSearch(
                StorySearchRequest.builder().tagContains("SLOW").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Story A");
    }

    // ── Filter by personal notes ──────────────────────────────────────────────

    @Test
    void search_filters_by_noteContains() {
        Story noted = storyRepository.saveAndFlush(Story.builder()
                .title("Noted Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .personalNotes("this one made me cry").user(user).build());
        storyRepository.saveAndFlush(Story.builder()
                .title("No Note Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .user(user).build());

        List<StoryResponse> results = storyService.advancedSearch(
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

        List<StoryResponse> results = storyService.advancedSearch(
                StorySearchRequest.builder().noteContains("cry").build());

        assertThat(results).extracting(StoryResponse::getTitle)
                .containsExactlyInAnyOrder("Noted Story");
    }

    // ── Filter by label ───────────────────────────────────────────────────────

    @Test
    void search_filters_by_labelId() {
        Label faveLabel = labelRepository.saveAndFlush(
                Label.builder().name("Favourites").user(user).build());
        Label otherLabel = labelRepository.saveAndFlush(
                Label.builder().name("Other").user(user).build());

        Story labelled = storyRepository.saveAndFlush(Story.builder()
                .title("Labelled Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .user(user).build());
        labelled.getLabels().add(faveLabel);
        storyRepository.saveAndFlush(labelled);

        Story unlabelled = storyRepository.saveAndFlush(Story.builder()
                .title("Unlabelled Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).status(StoryStatus.ONGOING).rating(Rating.NOT_RATED)
                .user(user).build());

        List<StoryResponse> results = storyService.advancedSearch(
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

        List<StoryResponse> results = storyService.advancedSearch(
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

        List<StoryResponse> results = storyService.advancedSearch(
                StorySearchRequest.builder().build());

        assertThat(results).hasSize(3);
    }
}
