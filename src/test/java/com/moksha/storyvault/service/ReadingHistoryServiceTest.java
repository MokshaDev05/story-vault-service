package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.ReadingHistoryRequest;
import com.moksha.storyvault.dto.ReadingHistoryResponse;
import com.moksha.storyvault.model.ReadingHistory;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.StoryStatus;
import com.moksha.storyvault.repository.ReadingHistoryRepository;
import com.moksha.storyvault.repository.StoryRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.impl.ReadingHistoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReadingHistoryServiceTest {

    @Mock ReadingHistoryRepository readingHistoryRepository;
    @Mock StoryRepository storyRepository;
    @Mock SecurityUtils securityUtils;

    @InjectMocks ReadingHistoryServiceImpl service;

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
                .status(StoryStatus.ONGOING)
                .rating(Rating.NOT_RATED)
                .user(user)
                .build();

        when(securityUtils.currentUser()).thenReturn(user);
        when(storyRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(story));
        when(storyRepository.save(any())).thenReturn(story);
        when(readingHistoryRepository.save(any(ReadingHistory.class))).thenAnswer(inv -> {
            ReadingHistory rh = inv.getArgument(0);
            if (rh.getAccessedAt() == null) rh.setAccessedAt(LocalDateTime.now());
            return rh;
        });
    }

    // ── Separate storage ──────────────────────────────────────────────────────

    @Test
    void log_stores_chapterNumber_and_chapterTitle_as_separate_fields() {
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(7)
                .chapterTitle("Into the Storm")
                .chapterUrl("https://archiveofourown.org/works/123/chapters/456")
                .sourcePlatform("AO3")
                .chapterAo3Id("456")
                .readingMode("CHAPTER")
                .build());

        ArgumentCaptor<ReadingHistory> cap = ArgumentCaptor.forClass(ReadingHistory.class);
        verify(readingHistoryRepository).save(cap.capture());
        ReadingHistory saved = cap.getValue();

        assertThat(saved.getChapterNumber()).isEqualTo(7);
        assertThat(saved.getChapterTitle()).isEqualTo("Into the Storm");
        assertThat(saved.getChapterAo3Id()).isEqualTo("456");
        assertThat(saved.getReadingMode()).isEqualTo("CHAPTER");
    }

    @Test
    void log_response_contains_separate_chapterNumber_and_chapterTitle() {
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        ReadingHistoryResponse resp = service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(3)
                .chapterTitle("A New Beginning")
                .sourcePlatform("AO3")
                .readingMode("CHAPTER")
                .build());

        assertThat(resp.getChapterNumber()).isEqualTo(3);
        assertThat(resp.getChapterTitle()).isEqualTo("A New Beginning");
    }

    // ── FULL_WORK and WORK_MAIN null-chapter contract ─────────────────────────

    @Test
    void log_full_work_mode_preserves_null_chapterNumber() {
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(null)
                .chapterTitle(null)
                .sourcePlatform("AO3")
                .readingMode("FULL_WORK")
                .build());

        ArgumentCaptor<ReadingHistory> cap = ArgumentCaptor.forClass(ReadingHistory.class);
        verify(readingHistoryRepository).save(cap.capture());

        assertThat(cap.getValue().getChapterNumber()).isNull();
        assertThat(cap.getValue().getReadingMode()).isEqualTo("FULL_WORK");
    }

    @Test
    void log_work_main_multi_chapter_preserves_null_chapterNumber() {
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        // Extension sends null currentChapter for WORK_MAIN on a multi-chapter work
        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(null)
                .sourcePlatform("AO3")
                .readingMode("WORK_MAIN")
                .build());

        ArgumentCaptor<ReadingHistory> cap = ArgumentCaptor.forClass(ReadingHistory.class);
        verify(readingHistoryRepository).save(cap.capture());

        assertThat(cap.getValue().getChapterNumber()).isNull();
    }

    // ── Deduplication ─────────────────────────────────────────────────────────

    @Test
    void log_skips_duplicate_chapterAo3Id_within_5_minutes() {
        ReadingHistory prev = ReadingHistory.builder()
                .id(99L)
                .story(story)
                .chapterNumber(3)
                .chapterAo3Id("789")
                .accessedAt(LocalDateTime.now().minusMinutes(2))
                .build();
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.of(prev));

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(3)
                .chapterAo3Id("789")
                .readingMode("CHAPTER")
                .sourcePlatform("AO3")
                .build());

        verify(readingHistoryRepository, never()).save(any(ReadingHistory.class));
    }

    @Test
    void log_skips_duplicate_chapterNumber_fallback_within_5_minutes() {
        // Both entries have null chapterAo3Id → fall back to chapterNumber equality
        ReadingHistory prev = ReadingHistory.builder()
                .id(99L)
                .story(story)
                .chapterNumber(5)
                .accessedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.of(prev));

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(5)
                .readingMode("CHAPTER")
                .sourcePlatform("AO3")
                .build());

        verify(readingHistoryRepository, never()).save(any(ReadingHistory.class));
    }

    @Test
    void log_allows_different_chapter_within_5_minutes() {
        ReadingHistory prev = ReadingHistory.builder()
                .id(99L)
                .story(story)
                .chapterNumber(3)
                .chapterAo3Id("789")
                .accessedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.of(prev));

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(4)
                .chapterAo3Id("790")
                .readingMode("CHAPTER")
                .sourcePlatform("AO3")
                .build());

        verify(readingHistoryRepository).save(any(ReadingHistory.class));
    }

    @Test
    void log_allows_same_chapter_after_dedup_window_expires() {
        ReadingHistory prev = ReadingHistory.builder()
                .id(99L)
                .story(story)
                .chapterNumber(3)
                .chapterAo3Id("789")
                .accessedAt(LocalDateTime.now().minusMinutes(6))
                .build();
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.of(prev));

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(3)
                .chapterAo3Id("789")
                .readingMode("CHAPTER")
                .sourcePlatform("AO3")
                .build());

        verify(readingHistoryRepository).save(any(ReadingHistory.class));
    }

    @Test
    void log_always_saves_when_no_prior_history() {
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(1)
                .readingMode("CHAPTER")
                .sourcePlatform("AO3")
                .build());

        verify(readingHistoryRepository).save(any(ReadingHistory.class));
    }

    // ── lastAccessedAt updated on story ──────────────────────────────────────

    @Test
    void log_updates_story_lastAccessedAt() {
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(2)
                .readingMode("CHAPTER")
                .sourcePlatform("AO3")
                .build());

        verify(storyRepository).updateLastAccessedAt(eq(10L), any(LocalDateTime.class));
    }

    // ── Stage 2: multiple events ──────────────────────────────────────────────

    @Test
    void repeated_visits_after_dedup_window_create_separate_rows() {
        // First visit: no prior history
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(5)
                .chapterAo3Id("500")
                .readingMode("CHAPTER")
                .sourcePlatform("AO3")
                .build());

        // Simulate time passing: the "top" row is now > 5 min old
        ReadingHistory firstEntry = ReadingHistory.builder()
                .id(101L)
                .story(story)
                .chapterNumber(5)
                .chapterAo3Id("500")
                .accessedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.of(firstEntry));

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(5)
                .chapterAo3Id("500")
                .readingMode("CHAPTER")
                .sourcePlatform("AO3")
                .build());

        // save() called twice — two separate rows
        verify(readingHistoryRepository, times(2)).save(any(ReadingHistory.class));
    }

    @Test
    void different_chapters_in_same_session_create_separate_rows() {
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());
        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(1).chapterAo3Id("100").readingMode("CHAPTER").sourcePlatform("AO3").build());

        ReadingHistory prevEntry = ReadingHistory.builder()
                .id(201L).story(story).chapterNumber(1).chapterAo3Id("100")
                .accessedAt(LocalDateTime.now().minusMinutes(1)).build();
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.of(prevEntry));

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(2).chapterAo3Id("200").readingMode("CHAPTER").sourcePlatform("AO3").build());

        verify(readingHistoryRepository, times(2)).save(any(ReadingHistory.class));
    }

    @Test
    void repeated_visit_within_dedup_window_does_not_create_extra_row() {
        ReadingHistory recent = ReadingHistory.builder()
                .id(301L).story(story).chapterNumber(3).chapterAo3Id("300")
                .accessedAt(LocalDateTime.now().minusMinutes(1)).build();
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.of(recent));

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(3).chapterAo3Id("300").readingMode("CHAPTER").sourcePlatform("AO3").build());

        verify(readingHistoryRepository, never()).save(any(ReadingHistory.class));
    }

    // ── Stage 2: event fields populated ──────────────────────────────────────

    @Test
    void log_populates_userId_workId_eventType_on_new_entry() {
        story.setSourceWorkId("12345678");
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(1)
                .readingMode("CHAPTER")
                .sourcePlatform("AO3")
                .build());

        ArgumentCaptor<ReadingHistory> cap = ArgumentCaptor.forClass(ReadingHistory.class);
        verify(readingHistoryRepository).save(cap.capture());
        ReadingHistory saved = cap.getValue();

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getWorkId()).isEqualTo("12345678");
        assertThat(saved.getEventType()).isEqualTo("PAGE_LOAD");
    }

    @Test
    void log_uses_PAGE_LOAD_default_when_eventType_not_provided() {
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(1).readingMode("CHAPTER").sourcePlatform("AO3")
                // eventType intentionally omitted
                .build());

        ArgumentCaptor<ReadingHistory> cap = ArgumentCaptor.forClass(ReadingHistory.class);
        verify(readingHistoryRepository).save(cap.capture());

        assertThat(cap.getValue().getEventType()).isEqualTo("PAGE_LOAD");
    }

    @Test
    void log_uses_caller_provided_eventType_when_given() {
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(1).readingMode("CHAPTER").sourcePlatform("AO3")
                .eventType("MANUAL")
                .build());

        ArgumentCaptor<ReadingHistory> cap = ArgumentCaptor.forClass(ReadingHistory.class);
        verify(readingHistoryRepository).save(cap.capture());

        assertThat(cap.getValue().getEventType()).isEqualTo("MANUAL");
    }

    // ── Stage 2: story fields preserved ──────────────────────────────────────

    @Test
    void log_does_not_modify_story_metadata_fields() {
        story.setSummary("Original summary with notes");
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(3).readingMode("CHAPTER").sourcePlatform("AO3").build());

        // story entity fields must be unchanged — targeted UPDATE replaces full save
        assertThat(story.getTitle()).isEqualTo("Test Story");
        assertThat(story.getAuthor()).isEqualTo("Author");
        assertThat(story.getSummary()).isEqualTo("Original summary with notes");
        verify(storyRepository).updateLastAccessedAt(eq(10L), any(LocalDateTime.class));
        verify(storyRepository, never()).save(any(Story.class));
    }

    @Test
    void log_does_not_modify_story_status_or_readingStatus() {
        story.setReadingStatus(com.moksha.storyvault.model.enums.ReadingStatus.ON_HOLD);
        when(readingHistoryRepository.findTopByStoryOrderByAccessedAtDesc(story))
                .thenReturn(Optional.empty());

        service.log(10L, ReadingHistoryRequest.builder()
                .chapterNumber(1).readingMode("CHAPTER").sourcePlatform("AO3").build());

        // story entity state must be untouched — targeted UPDATE only touches lastAccessedAt
        assertThat(story.getReadingStatus()).isEqualTo(com.moksha.storyvault.model.enums.ReadingStatus.ON_HOLD);
        assertThat(story.getStatus()).isEqualTo(StoryStatus.ONGOING);
        verify(storyRepository).updateLastAccessedAt(eq(10L), any(LocalDateTime.class));
        verify(storyRepository, never()).save(any(Story.class));
    }

    // ── listByStory ───────────────────────────────────────────────────────────

    @Test
    void listByStory_returns_empty_list_when_no_history() {
        when(readingHistoryRepository.findByStoryOrderByAccessedAtDesc(story))
                .thenReturn(java.util.List.of());

        java.util.List<ReadingHistoryResponse> result = service.listByStory(10L);

        assertThat(result).isEmpty();
    }

    @Test
    void listByStory_returns_all_entries_newest_first() {
        LocalDateTime t1 = LocalDateTime.now().minusHours(2);
        LocalDateTime t2 = LocalDateTime.now().minusHours(1);
        ReadingHistory older = ReadingHistory.builder()
                .id(1L).story(story).chapterNumber(1).accessedAt(t1).eventType("PAGE_LOAD").build();
        ReadingHistory newer = ReadingHistory.builder()
                .id(2L).story(story).chapterNumber(2).accessedAt(t2).eventType("PAGE_LOAD").build();

        // Repository returns desc — newer first
        when(readingHistoryRepository.findByStoryOrderByAccessedAtDesc(story))
                .thenReturn(java.util.List.of(newer, older));

        java.util.List<ReadingHistoryResponse> result = service.listByStory(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getChapterNumber()).isEqualTo(2);
        assertThat(result.get(1).getChapterNumber()).isEqualTo(1);
    }

    @Test
    void listByStory_response_contains_expected_fields() {
        LocalDateTime now = LocalDateTime.now();
        ReadingHistory entry = ReadingHistory.builder()
                .id(5L).story(story)
                .chapterNumber(3).chapterTitle("Storm Front")
                .chapterUrl("https://archiveofourown.org/works/99/chapters/123")
                .sourcePlatform("AO3").chapterAo3Id("123")
                .readingMode("CHAPTER").eventType("PAGE_LOAD")
                .userId(1L).workId("99").accessedAt(now)
                .build();

        when(readingHistoryRepository.findByStoryOrderByAccessedAtDesc(story))
                .thenReturn(java.util.List.of(entry));

        java.util.List<ReadingHistoryResponse> result = service.listByStory(10L);

        ReadingHistoryResponse r = result.get(0);
        assertThat(r.getId()).isEqualTo(5L);
        assertThat(r.getStoryId()).isEqualTo(10L);
        assertThat(r.getChapterNumber()).isEqualTo(3);
        assertThat(r.getChapterTitle()).isEqualTo("Storm Front");
        assertThat(r.getReadingMode()).isEqualTo("CHAPTER");
        assertThat(r.getEventType()).isEqualTo("PAGE_LOAD");
        assertThat(r.getUserId()).isEqualTo(1L);
        assertThat(r.getWorkId()).isEqualTo("99");
        assertThat(r.getAccessedAt()).isEqualTo(now);
    }
}
