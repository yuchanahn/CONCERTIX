package com.example.demo.auth;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 100) String nickname) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record MemberResponse(UUID id, String email, String nickname, String role) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long accessExpiresInSeconds,
            MemberResponse member) {
    }
}
