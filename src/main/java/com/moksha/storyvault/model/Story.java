package com.moksha.storyvault.model;

import com.moksha.storyvault.model.enums.KudosStatus;
import com.moksha.storyvault.model.enums.Platform;
import com.moksha.storyvault.model.enums.Rating;
import com.moksha.storyvault.model.enums.ReadingStatus;
import com.moksha.storyvault.model.enums.StoryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "stories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String fandom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private StoryStatus status = StoryStatus.ONGOING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private Rating rating = Rating.NOT_RATED;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "original_url", length = 2048)
    private String originalUrl;

    @Column(name = "source_work_id", length = 100)
    private String sourceWorkId;

    private Integer wordCount;

    /** Published chapter count (how many chapters are currently on AO3). */
    private Integer chapterCount;

    /** Planned total chapters; null means unknown (?). */
    @Column(name = "total_chapters")
    private Integer totalChapters;

    @Column(name = "ao3_published_date")
    private LocalDate ao3PublishedDate;

    @Column(name = "ao3_updated_date")
    private LocalDate ao3UpdatedDate;

    @Column(name = "language", length = 100)
    private String language;

    private LocalDate completedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "story_tags",
        joinColumns = @JoinColumn(name = "story_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "story_relationships", joinColumns = @JoinColumn(name = "story_id"))
    @Column(name = "relationship", length = 500)
    @Builder.Default
    private List<String> relationships = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "story_characters", joinColumns = @JoinColumn(name = "story_id"))
    @Column(name = "character_name", length = 500)
    @Builder.Default
    private List<String> characters = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "story_warnings", joinColumns = @JoinColumn(name = "story_id"))
    @Column(name = "warning", length = 500)
    @Builder.Default
    private List<String> archiveWarnings = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "story_categories", joinColumns = @JoinColumn(name = "story_id"))
    @Column(name = "category", length = 100)
    @Builder.Default
    private List<String> categories = new ArrayList<>();

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReadingHistory> readingHistory = new ArrayList<>();

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Note> notes = new ArrayList<>();

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DownloadRecord> downloadRecords = new ArrayList<>();

    @OneToOne(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    private StoryFile storyFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "reading_status", length = 50)
    private ReadingStatus readingStatus;

    @Column(name = "current_chapter")
    private Integer currentChapter;

    @Column(name = "current_chapter_url", length = 2048)
    private String currentChapterUrl;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "kudos_status", length = 20)
    @Builder.Default
    private KudosStatus kudosStatus = KudosStatus.UNKNOWN;

    @Column(name = "kudos_detected_at")
    private LocalDateTime kudosDetectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id")
    private ConnectedAccount sourceAccount;

    @ManyToMany(mappedBy = "stories", fetch = FetchType.LAZY)
    @BatchSize(size = 30)
    @Builder.Default
    private Set<Shelf> collections = new HashSet<>();
}
