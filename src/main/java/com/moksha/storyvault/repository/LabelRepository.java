package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.Label;
import com.moksha.storyvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabelRepository extends JpaRepository<Label, Long> {

    List<Label> findAllByUserOrderByNameAsc(User user);

    Optional<Label> findByIdAndUser(Long id, User user);

    boolean existsByNameIgnoreCaseAndUser(String name, User user);

    @Query("SELECT l.name, COUNT(s.id) FROM Label l JOIN l.stories s WHERE l.user = :user GROUP BY l.name ORDER BY COUNT(s.id) DESC")
    List<Object[]> topLabelsByUser(@Param("user") User user);

    @Query("SELECT COUNT(DISTINCT s.id) FROM Label l JOIN l.stories s WHERE l.user = :user")
    long countDistinctLabeledStoriesByUser(@Param("user") User user);
}
