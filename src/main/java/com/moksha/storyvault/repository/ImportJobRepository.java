package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.ImportJob;
import com.moksha.storyvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    List<ImportJob> findAllByUserOrderByCreatedAtDesc(User user);
    Optional<ImportJob> findByIdAndUser(Long id, User user);
}
