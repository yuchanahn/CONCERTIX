package com.example.demo.payment;

import java.time.Instant;
import java.util.UUID;

import com.example.demo.reservation.Reservation;

import jakarta.validation.constraints.NotNull;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record CreateRequest(@NotNull UUID reservationId) {
    }

    public record CompleteRequest(@NotNull PaymentResult result) {
    }

    public record Response(
            UUID id,
            UUID reservationId,
            long amount,
            PaymentStatus status,
            String reservationStatus,
            String seatStatus,
            Instant createdAt,
            Instant updatedAt) {

        public static Response from(Payment payment) {
            Reservation reservation = payment.getReservation();
            return new Response(
                    payment.getId(),
                    reservation.getId(),
                    payment.getAmount(),
                    payment.getStatus(),
                    reservation.getStatus().name(),
                    reservation.getSeat().getStatus().name(),
                    payment.getCreatedAt(),
                    payment.getUpdatedAt());
        }
    }
}
