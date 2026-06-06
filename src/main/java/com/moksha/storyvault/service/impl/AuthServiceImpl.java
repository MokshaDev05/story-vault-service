package com.moksha.storyvault.service.impl;

import com.moksha.storyvault.dto.AuthResponse;
import com.moksha.storyvault.dto.LoginRequest;
import com.moksha.storyvault.dto.RegisterRequest;
import com.moksha.storyvault.exception.UserAlreadyExistsException;
import com.moksha.storyvault.model.User;
import com.moksha.storyvault.repository.UserRepository;
import com.moksha.storyvault.security.JwtService;
import com.moksha.storyvault.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService, UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Autowired
    @Lazy
    private AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException(request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);
        log.info("Registered new user: {}", request.getUsername());

        String token = jwtService.generateToken(user.getUsername());
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = jwtService.generateToken(user.getUsername());
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .build();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
