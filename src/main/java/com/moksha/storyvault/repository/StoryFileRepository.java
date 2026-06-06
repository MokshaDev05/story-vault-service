package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.StoryFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoryFileRepository extends JpaRepository<StoryFile, Long> {

    Optional<StoryFile> findByStoryId(Long storyId);

    boolean existsByStoryId(Long storyId);
}
