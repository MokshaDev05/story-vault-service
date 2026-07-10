package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);

    @Query("SELECT t FROM Tag t WHERE t.name IN :names")
    List<Tag> findAllByNameIn(@Param("names") Set<String> names);
}
