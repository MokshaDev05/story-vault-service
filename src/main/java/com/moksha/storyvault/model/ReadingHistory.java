package com.moksha.storyvault.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reading_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(nullable = false, updatable = false)
    private LocalDateTime accessedAt;

    private Integer chapterNumber;

    @Column(length = 500)
    private String chapterTitle;

    @Column(length = 2048)
    private String chapterUrl;

    @Column(length = 50)
    private String sourcePlatform;

    /** AO3 chapter ID extracted from the /chapters/{id} URL segment. */
    @Column(name = "chapter_ao3_id", length = 50)
    private String chapterAo3Id;

    /** CHAPTER | WORK_MAIN | FULL_WORK — how the page was loaded. */
    @Column(name = "reading_mode", length = 20)
    private String readingMode;

    /** Denormalised user ID — avoids JOIN when querying history by user. */
    @Column(name = "user_id")
    private Long userId;

    /** Denormalised AO3 / source work ID from the story. */
    @Column(name = "work_id", length = 100)
    private String workId;

    /** PAGE_LOAD — auto-tracked; MANUAL — user-entered. */
    @Column(name = "event_type", length = 50)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private ConnectedAccount sourceAccount;
}
