package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.ConnectedAccountRequest;
import com.moksha.storyvault.dto.ConnectedAccountResponse;
import com.moksha.storyvault.exception.ConnectedAccountNotFoundException;
import com.moksha.storyvault.model.ConnectedAccount;
import com.moksha.storyvault.repository.ConnectedAccountRepository;
import com.moksha.storyvault.security.SecurityUtils;
import com.moksha.storyvault.service.ConnectedAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConnectedAccountServiceImpl implements ConnectedAccountService {

    private final ConnectedAccountRepository accountRepository;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public ConnectedAccountResponse create(ConnectedAccountRequest request) {
        var user = securityUtils.currentUser();
        ConnectedAccount account = ConnectedAccount.builder()
                .user(user)
                .platform(request.getPlatform())
                .displayName(request.getDisplayName())
                .profileUrl(request.getProfileUrl())
                .accountLabel(request.getAccountLabel())
                .syncEnabled(request.getSyncEnabled() != null ? request.getSyncEnabled() : true)
                .notes(request.getNotes())
                .build();
        return toResponse(accountRepository.save(account));
    }

    @Override
    public List<ConnectedAccountResponse> listAll() {
        var user = securityUtils.currentUser();
        return accountRepository.findAllByUserOrderByCreatedAtAsc(user).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ConnectedAccountResponse findById(Long id) {
        var user = securityUtils.currentUser();
        return accountRepository.findByIdAndUser(id, user)
                .map(this::toResponse)
                .orElseThrow(() -> new ConnectedAccountNotFoundException(id));
    }

    @Override
    @Transactional
    public ConnectedAccountResponse update(Long id, ConnectedAccountRequest request) {
        var user = securityUtils.currentUser();
        ConnectedAccount account = accountRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ConnectedAccountNotFoundException(id));

        account.setPlatform(request.getPlatform());
        account.setDisplayName(request.getDisplayName());
        account.setProfileUrl(request.getProfileUrl());
        account.setAccountLabel(request.getAccountLabel());
        if (request.getSyncEnabled() != null) account.setSyncEnabled(request.getSyncEnabled());
        account.setNotes(request.getNotes());

        return toResponse(accountRepository.save(account));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        var user = securityUtils.currentUser();
        ConnectedAccount account = accountRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ConnectedAccountNotFoundException(id));
        accountRepository.delete(account);
    }

    private ConnectedAccountResponse toResponse(ConnectedAccount account) {
        return ConnectedAccountResponse.builder()
                .id(account.getId())
                .platform(account.getPlatform())
                .displayName(account.getDisplayName())
                .profileUrl(account.getProfileUrl())
                .accountLabel(account.getAccountLabel())
                .syncEnabled(account.getSyncEnabled())
                .notes(account.getNotes())
                .lastSyncAt(account.getLastSyncAt())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
