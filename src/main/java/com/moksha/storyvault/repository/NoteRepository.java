package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByStoryIdOrderByCreatedAtDesc(Long storyId);
}
