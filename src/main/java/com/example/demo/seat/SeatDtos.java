package com.example.demo.seat;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SeatDtos {

    private SeatDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 50) String section,
            @NotBlank @Size(max = 20) String row,
            @Min(1) @Max(100000) int startNumber,
            @Min(1) @Max(10000) int count,
            @Min(0) @Max(1000000000) long price) {
    }

    public record StatusRequest(@NotNull SeatStatus status) {
    }

    public record Response(
            UUID id,
            UUID sessionId,
            String section,
            String row,
            int number,
            long price,
            String status) {
    }
}

