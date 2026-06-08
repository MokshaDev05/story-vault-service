package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.ImportJob;
import com.moksha.storyvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    List<ImportJob> findAllByUserOrderByCreatedAtDesc(User user);
}
