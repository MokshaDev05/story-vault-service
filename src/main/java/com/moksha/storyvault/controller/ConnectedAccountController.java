package com.moksha.storyvault.controller;

import com.moksha.storyvault.dto.ApiResponse;
import com.moksha.storyvault.dto.ConnectedAccountRequest;
import com.moksha.storyvault.dto.ConnectedAccountResponse;
import com.moksha.storyvault.service.ConnectedAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class ConnectedAccountController {

    private final ConnectedAccountService accountService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConnectedAccountResponse>> create(
            @Valid @RequestBody ConnectedAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created", accountService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConnectedAccountResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success("Accounts retrieved", accountService.listAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConnectedAccountResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Account retrieved", accountService.findById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ConnectedAccountResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ConnectedAccountRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Account updated", accountService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
