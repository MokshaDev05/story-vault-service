package com.moksha.storyvault.repository;

import com.moksha.storyvault.model.ConnectedAccount;
import com.moksha.storyvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccount, Long> {

    List<ConnectedAccount> findAllByUserOrderByCreatedAtAsc(User user);

    Optional<ConnectedAccount> findByIdAndUser(Long id, User user);

    long countByUser(User user);
}
