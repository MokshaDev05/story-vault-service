package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.Shelf;
import com.moksha.storyvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShelfRepository extends JpaRepository<Shelf, Long> {

    @Query("SELECT DISTINCT sh FROM Shelf sh LEFT JOIN FETCH sh.stories WHERE sh.user = :user ORDER BY sh.name ASC")
    List<Shelf> findAllWithStoriesByUser(@Param("user") User user);

    Optional<Shelf> findByIdAndUser(Long id, User user);

    Optional<Shelf> findByNameAndUser(String name, User user);

    long countByUser(User user);
}
