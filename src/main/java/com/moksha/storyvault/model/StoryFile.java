package com.moksha.storyvault.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "story_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false, unique = true)
    private Story story;

    @Column(nullable = false, length = 500)
    private String filename;

    @Column(nullable = false, length = 255)
    private String contentType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private byte[] fileData;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}
