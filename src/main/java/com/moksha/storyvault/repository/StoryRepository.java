package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.Platform;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long>, JpaSpecificationExecutor<Story> {

    @Query("SELECT DISTINCT s FROM Story s LEFT JOIN FETCH s.tags WHERE s.user = :user ORDER BY s.createdAt DESC")
    List<Story> findAllWithTagsByUser(@Param("user") User user);

    Optional<Story> findByIdAndUser(Long id, User user);

    Optional<Story> findByTitleIgnoreCaseAndAuthorIgnoreCaseAndUser(String title, String author, User user);

    Optional<Story> findByPlatformAndSourceWorkIdAndUser(Platform platform, String sourceWorkId, User user);

    Optional<Story> findByOriginalUrlAndUser(String originalUrl, User user);

    List<Story> findByUserIsNull();

    @Query("SELECT COUNT(s) FROM Story s WHERE s.user = :user")
    long countByUser(@Param("user") User user);

    @Query("SELECT COALESCE(SUM(s.wordCount), 0) FROM Story s WHERE s.user = :user")
    long sumWordCountByUser(@Param("user") User user);

    @Query("SELECT s.status, COUNT(s) FROM Story s WHERE s.user = :user GROUP BY s.status")
    List<Object[]> countByStoryStatusForUser(@Param("user") User user);

    @Query("SELECT s.readingStatus, COUNT(s) FROM Story s WHERE s.user = :user AND s.readingStatus IS NOT NULL GROUP BY s.readingStatus")
    List<Object[]> countByReadingStatusForUser(@Param("user") User user);

    @Query("SELECT COUNT(s) FROM Story s WHERE s.user = :user AND s.kudosStatus = com.moksha.storyvault.model.enums.KudosStatus.GIVEN")
    long countKudosedByUser(@Param("user") User user);

    @Query("SELECT s.fandom, COUNT(s) FROM Story s WHERE s.user = :user GROUP BY s.fandom ORDER BY COUNT(s) DESC")
    List<Object[]> topFandomsByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT s.author, COUNT(s) FROM Story s WHERE s.user = :user GROUP BY s.author ORDER BY COUNT(s) DESC")
    List<Object[]> topAuthorsByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT r, COUNT(s.id) FROM Story s JOIN s.relationships r WHERE s.user = :user GROUP BY r ORDER BY COUNT(s.id) DESC")
    List<Object[]> topRelationshipsByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT t.name, COUNT(s.id) FROM Story s JOIN s.tags t WHERE s.user = :user GROUP BY t.name ORDER BY COUNT(s.id) DESC")
    List<Object[]> topTagsByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT s.id, s.title, s.lastAccessedAt FROM Story s WHERE s.user = :user AND s.lastAccessedAt IS NOT NULL ORDER BY s.lastAccessedAt DESC")
    List<Object[]> recentlyAccessedByUser(@Param("user") User user, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Story s WHERE s.user = :user AND s.personalNotes IS NOT NULL AND s.personalNotes <> ''")
    long countStoriesWithNotesByUser(@Param("user") User user);

    @Query("SELECT COUNT(DISTINCT s.id) FROM Story s JOIN s.labels l WHERE s.user = :user")
    long countLabeledStoriesByUser(@Param("user") User user);
}
