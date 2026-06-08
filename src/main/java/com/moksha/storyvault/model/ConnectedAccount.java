package com.moksha.storyvault.model;

import com.moksha.storyvault.model.enums.Platform;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "connected_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectedAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Platform platform;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "profile_url", length = 2048)
    private String profileUrl;

    @Column(name = "account_label", length = 100)
    private String accountLabel;

    @Column(name = "sync_enabled", nullable = false)
    @Builder.Default
    private Boolean syncEnabled = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
