package com.moksha.storyvault.repository;

import com.moksha.storyvault.dto.ReadingHistoryStats;
import com.moksha.storyvault.model.ReadingHistory;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingHistoryRepository extends JpaRepository<ReadingHistory, Long> {

    List<ReadingHistory> findByStoryOrderByAccessedAtDesc(Story story);

    Optional<ReadingHistory> findTopByStoryOrderByAccessedAtDesc(Story story);

    boolean existsByStoryAndEventTypeAndAccessedAtBetween(
            Story story, String eventType, LocalDateTime from, LocalDateTime to);

    Optional<ReadingHistory> findTopByStoryAndEventTypeOrderByAccessedAtDesc(
            Story story, String eventType);

    Optional<ReadingHistory> findTopByStoryAndEventTypeAndAccessedAtBetweenOrderByAccessedAtDesc(
            Story story, String eventType, LocalDateTime from, LocalDateTime to);

    @Query("""
        SELECT new com.moksha.storyvault.dto.ReadingHistoryStats(
            h.story.id, MIN(h.accessedAt), COUNT(h))
        FROM ReadingHistory h
        WHERE h.story.id IN :storyIds
        GROUP BY h.story.id
    """)
    List<ReadingHistoryStats> findStatsByStoryIds(@Param("storyIds") List<Long> storyIds);

    @Query("""
        SELECT DISTINCT h.story.id
        FROM ReadingHistory h
        WHERE h.story.user = :user
          AND h.chapterNumber = :chapterNumber
    """)
    List<Long> findStoryIdsWithChapterAccessed(@Param("user") User user,
                                               @Param("chapterNumber") Integer chapterNumber);

    @Query("""
        SELECT h.story.id, h.story.title, COUNT(h), h.story.chapterCount
        FROM ReadingHistory h
        WHERE h.story.user = :user
        GROUP BY h.story.id, h.story.title, h.story.chapterCount
        ORDER BY COUNT(h) DESC
    """)
    List<Object[]> mostAccessedStoriesByUser(@Param("user") User user, Pageable pageable);

    @Query(value = """
        SELECT bucket, COUNT(DISTINCT story_id) AS distinct_works
        FROM (
            SELECT CAST(DATE_TRUNC(:period, accessed_at) AS text) AS bucket,
                   story_id
            FROM reading_history
            WHERE user_id = :userId
        ) sub
        GROUP BY bucket
        ORDER BY bucket
    """, nativeQuery = true)
    List<Object[]> activityByPeriod(@Param("userId") Long userId, @Param("period") String period);
}
