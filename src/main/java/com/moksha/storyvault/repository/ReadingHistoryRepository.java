package com.moksha.storyvault.repository;

import com.moksha.storyvault.dto.ReadingHistoryStats;
import com.moksha.storyvault.model.ReadingHistory;
import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReadingHistoryRepository extends JpaRepository<ReadingHistory, Long> {

    List<ReadingHistory> findByStoryOrderByAccessedAtDesc(Story story);

    Optional<ReadingHistory> findTopByStoryOrderByAccessedAtDesc(Story story);

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
}
