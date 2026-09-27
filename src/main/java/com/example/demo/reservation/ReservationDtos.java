package com.example.demo.reservation;

import java.time.Instant;
import java.util.UUID;

import com.example.demo.seat.Seat;

import jakarta.validation.constraints.NotNull;

public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record CreateReservationRequest(
            @NotNull UUID sessionId,
            @NotNull UUID seatId) {
    }

    public record SeatInfoDto(
            String section,
            String rowLabel,
            int seatNumber,
            long price,
            Instant startTime) {

        public static SeatInfoDto from(Seat seat) {
            return new SeatInfoDto(
                    seat.getSection(),
                    seat.getRowLabel(),
                    seat.getSeatNumber(),
                    seat.getPrice(),
                    seat.getSession().getStartsAt());
        }
    }

    public record ReservationResponse(
            UUID id,
            UUID sessionId,
            UUID seatId,
            SeatInfoDto seatInfo,
            ReservationStatus status,
            long price,
            Instant heldUntil,
            Instant createdAt) {

        public static ReservationResponse from(Reservation reservation) {
            return new ReservationResponse(
                    reservation.getId(),
                    reservation.getSession().getId(),
                    reservation.getSeat().getId(),
                    SeatInfoDto.from(reservation.getSeat()),
                    reservation.getStatus(),
                    reservation.getPrice(),
                    reservation.getHeldUntil(),
                    reservation.getCreatedAt());
        }
    }
}
