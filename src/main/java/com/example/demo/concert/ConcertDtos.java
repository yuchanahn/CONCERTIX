package com.example.demo.concert;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ConcertDtos {

    private ConcertDtos() {
    }

    public record Request(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotBlank @Size(max = 2048) @Pattern(regexp = "https?://.+") String imageUrl,
            @NotBlank @Size(max = 200) String venue,
            @Min(1) @Max(3600) int holdSeconds,
            @Min(1) @Max(100) int maxTicketsPerUser,
            @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid SessionRequest> sessions) {
    }

    public record SessionRequest(
            @NotNull Instant startsAt,
            @NotNull Instant bookingOpensAt,
            @NotNull Instant bookingClosesAt,
            @Min(1) @Max(100000) int capacity) {
    }

    public record SessionResponse(
            UUID id,
            Instant startsAt,
            Instant bookingOpensAt,
            Instant bookingClosesAt,
            int capacity) {
    }

    public record Response(
            UUID id,
            String title,
            String description,
            String imageUrl,
            String venue,
            int holdSeconds,
            int maxTicketsPerUser,
            String status,
            Instant createdAt,
            List<SessionResponse> sessions) {
    }

    public record PageResponse(List<Summary> content, int page, int size, long totalElements, int totalPages) {
    }

    public record Summary(UUID id, String title, String imageUrl, String venue, String status, Instant createdAt) {
    }
}
