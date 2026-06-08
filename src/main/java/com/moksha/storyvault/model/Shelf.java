package com.moksha.storyvault.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "shelves")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shelf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "shelf_stories",
        joinColumns = @JoinColumn(name = "shelf_id"),
        inverseJoinColumns = @JoinColumn(name = "story_id")
    )
    @Builder.Default
    private Set<Story> stories = new HashSet<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
