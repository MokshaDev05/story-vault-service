package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.ConnectedAccountRequest;
import com.moksha.storyvault.dto.ConnectedAccountResponse;

import java.util.List;

public interface ConnectedAccountService {
    ConnectedAccountResponse create(ConnectedAccountRequest request);
    List<ConnectedAccountResponse> listAll();
    ConnectedAccountResponse findById(Long id);
    ConnectedAccountResponse update(Long id, ConnectedAccountRequest request);
    void delete(Long id);
}
