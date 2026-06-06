package com.moksha.storyvault.service;

import com.moksha.storyvault.dto.AuthResponse;
import com.moksha.storyvault.dto.LoginRequest;
import com.moksha.storyvault.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
