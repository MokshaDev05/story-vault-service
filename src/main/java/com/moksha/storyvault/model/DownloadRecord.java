package com.moksha.storyvault.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "download_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(length = 500)
    private String source;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime downloadedAt;
}
