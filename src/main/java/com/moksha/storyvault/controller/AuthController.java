package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.AuthResponse;
import com.moksha.storyvault.dto.LoginRequest;
import com.moksha.storyvault.dto.RegisterRequest;
import com.moksha.storyvault.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse auth = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", auth));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse auth = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", auth));
    }
}
