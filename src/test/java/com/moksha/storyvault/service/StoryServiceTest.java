package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.StoryRequest;
import com.moksha.storyvault.dto.StoryResponse;
import com.moksha.storyvault.dto.UpsertResult;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.KudosStatus;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.ReadingStatus;
import com.moksha.storyvault.model.enums.StoryStatus;
import com.moksha.storyvault.repository.ConnectedAccountRepository;
import com.moksha.storyvault.repository.ReadingHistoryRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.repository.TagRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.impl.StoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoryServiceTest {

    @Mock StoryRepository storyRepository;
    @Mock TagRepository tagRepository;
    @Mock ReadingHistoryRepository readingHistoryRepository;
    @Mock ConnectedAccountRepository connectedAccountRepository;
    @Mock SecurityUtils securityUtils;

    @InjectMocks StoryServiceImpl service;

    private User user;
    private Story story;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("alice").password("x").build();
        story = Story.builder()
                .id(10L)
                .title("Test Story")
                .author("Author")
                .fandom("Fandom")
                .platform(Platform.AO3)
                .sourceWorkId("99999")
                .status(StoryStatus.ONGOING)
                .rating(Rating.NOT_RATED)
                .readingStatus(ReadingStatus.STILL_READING)
                .user(user)
                .build();

        when(securityUtils.currentUser()).thenReturn(user);
        when(storyRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(story));
        when(storyRepository.findByPlatformAndSourceWorkIdAndUser(Platform.AO3, "99999", user))
                .thenReturn(Optional.of(story));
        when(storyRepository.save(any(Story.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── findById includes currentChapterUrl ───────────────────────────────────

    @Test
    void findById_includes_currentChapterUrl_when_set() {
        story.setCurrentChapter(7);
        story.setCurrentChapterUrl("https://archiveofourown.org/works/99999/chapters/12345");

        StoryResponse resp = service.findById(10L);

        assertThat(resp.getCurrentChapterUrl())
                .isEqualTo("https://archiveofourown.org/works/99999/chapters/12345");
    }

    @Test
    void findById_returns_null_currentChapterUrl_when_not_set() {
        StoryResponse resp = service.findById(10L);

        assertThat(resp.getCurrentChapterUrl()).isNull();
    }

    // ── advanceReadingProgress via upsert ────────────────────────────────────

    @Test
    void upsert_advances_currentChapterUrl_when_chapter_moves_forward() {
        story.setCurrentChapter(3);
        story.setCurrentChapterUrl("https://archiveofourown.org/works/99999/chapters/100");

        UpsertResult result = service.upsert(StoryRequest.builder()
                .title("Test Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).sourceWorkId("99999")
                .currentChapter(5)
                .currentChapterUrl("https://archiveofourown.org/works/99999/chapters/200")
                .build());

        assertThat(result.story().getCurrentChapter()).isEqualTo(5);
        assertThat(result.story().getCurrentChapterUrl())
                .isEqualTo("https://archiveofourown.org/works/99999/chapters/200");
    }

    @Test
    void upsert_does_not_update_url_when_chapter_goes_backward() {
        story.setCurrentChapter(5);
        story.setCurrentChapterUrl("https://archiveofourown.org/works/99999/chapters/200");

        UpsertResult result = service.upsert(StoryRequest.builder()
                .title("Test Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).sourceWorkId("99999")
                .currentChapter(3)
                .currentChapterUrl("https://archiveofourown.org/works/99999/chapters/100")
                .build());

        assertThat(result.story().getCurrentChapter()).isEqualTo(5);
        assertThat(result.story().getCurrentChapterUrl())
                .isEqualTo("https://archiveofourown.org/works/99999/chapters/200");
    }

    @Test
    void upsert_does_not_update_url_for_FINISHED_READING_story() {
        story.setReadingStatus(ReadingStatus.FINISHED_READING);
        story.setCurrentChapter(20);
        story.setCurrentChapterUrl("https://archiveofourown.org/works/99999/chapters/final");

        UpsertResult result = service.upsert(StoryRequest.builder()
                .title("Test Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).sourceWorkId("99999")
                .currentChapter(1)
                .currentChapterUrl("https://archiveofourown.org/works/99999/chapters/first")
                .build());

        assertThat(result.story().getCurrentChapter()).isEqualTo(20);
        assertThat(result.story().getCurrentChapterUrl())
                .isEqualTo("https://archiveofourown.org/works/99999/chapters/final");
    }

    // ── Kudos status merge ────────────────────────────────────────────────────

    @Test
    void upsert_sets_kudosStatus_GIVEN_when_current_is_UNKNOWN() {
        story.setKudosStatus(KudosStatus.UNKNOWN);

        UpsertResult result = service.upsert(StoryRequest.builder()
                .title("Test Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).sourceWorkId("99999")
                .kudosStatus(KudosStatus.GIVEN)
                .build());

        assertThat(result.story().getKudosStatus()).isEqualTo(KudosStatus.GIVEN);
    }

    @Test
    void upsert_sets_kudosStatus_NOT_DETECTED_when_current_is_UNKNOWN() {
        story.setKudosStatus(KudosStatus.UNKNOWN);

        UpsertResult result = service.upsert(StoryRequest.builder()
                .title("Test Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).sourceWorkId("99999")
                .kudosStatus(KudosStatus.NOT_DETECTED)
                .build());

        assertThat(result.story().getKudosStatus()).isEqualTo(KudosStatus.NOT_DETECTED);
    }

    @Test
    void upsert_GIVEN_overwrites_NOT_DETECTED_because_GIVEN_always_wins() {
        story.setKudosStatus(KudosStatus.NOT_DETECTED);

        UpsertResult result = service.upsert(StoryRequest.builder()
                .title("Test Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).sourceWorkId("99999")
                .kudosStatus(KudosStatus.GIVEN)
                .build());

        assertThat(result.story().getKudosStatus()).isEqualTo(KudosStatus.GIVEN);
    }

    @Test
    void upsert_does_not_overwrite_NOT_DETECTED_with_NOT_DETECTED() {
        story.setKudosStatus(KudosStatus.NOT_DETECTED);

        UpsertResult result = service.upsert(StoryRequest.builder()
                .title("Test Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).sourceWorkId("99999")
                .kudosStatus(KudosStatus.NOT_DETECTED)
                .build());

        // Status stays NOT_DETECTED; kudosDetectedAt should NOT be refreshed
        assertThat(result.story().getKudosStatus()).isEqualTo(KudosStatus.NOT_DETECTED);
    }

    @Test
    void upsert_ignores_incoming_UNKNOWN_kudosStatus() {
        story.setKudosStatus(KudosStatus.NOT_DETECTED);

        UpsertResult result = service.upsert(StoryRequest.builder()
                .title("Test Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).sourceWorkId("99999")
                .kudosStatus(KudosStatus.UNKNOWN)
                .build());

        // Incoming UNKNOWN must never overwrite a real detected status
        assertThat(result.story().getKudosStatus()).isEqualTo(KudosStatus.NOT_DETECTED);
    }

    @Test
    void findById_includes_originalUrl_as_continue_reading_fallback() {
        // When no currentChapterUrl is set, the frontend falls back to originalUrl.
        // Both fields must be present in the response so the frontend can evaluate
        // `currentChapterUrl || originalUrl` without a server round-trip.
        story.setOriginalUrl("https://archiveofourown.org/works/99999");

        StoryResponse resp = service.findById(10L);

        assertThat(resp.getCurrentChapterUrl()).isNull();
        assertThat(resp.getOriginalUrl()).isEqualTo("https://archiveofourown.org/works/99999");
    }

    @Test
    void upsert_does_not_update_url_for_DNF_story() {
        story.setReadingStatus(ReadingStatus.DNF);
        story.setCurrentChapter(4);
        story.setCurrentChapterUrl("https://archiveofourown.org/works/99999/chapters/old");

        UpsertResult result = service.upsert(StoryRequest.builder()
                .title("Test Story").author("Author").fandom("Fandom")
                .platform(Platform.AO3).sourceWorkId("99999")
                .currentChapter(6)
                .currentChapterUrl("https://archiveofourown.org/works/99999/chapters/new")
                .build());

        assertThat(result.story().getCurrentChapterUrl())
                .isEqualTo("https://archiveofourown.org/works/99999/chapters/old");
    }
}
