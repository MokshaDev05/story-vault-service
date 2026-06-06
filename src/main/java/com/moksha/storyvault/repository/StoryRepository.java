package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.Story;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.model.enums.Platform;
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
}
