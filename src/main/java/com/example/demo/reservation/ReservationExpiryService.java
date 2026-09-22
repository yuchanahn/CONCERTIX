package com.example.demo.reservation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.seat.Seat;
import com.example.demo.seat.SeatRepository;
import com.example.demo.seat.SeatStatus;

@Service
public class ReservationExpiryService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    public ReservationExpiryService(
            ReservationRepository reservationRepository,
            SeatRepository seatRepository) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public void expireHoldsForSession(UUID sessionId) {
        Instant now = Instant.now();
        for (UUID seatId : reservationRepository.findSeatIdsWithExpiredHolds(
                sessionId,
                ReservationStatus.PENDING_PAYMENT,
                now)) {
            Seat seat = seatRepository.findByIdForUpdate(seatId).orElse(null);
            if (seat == null) {
                continue;
            }

            reservationRepository.findExpiredHolds(
                    seatId,
                    ReservationStatus.PENDING_PAYMENT,
                    now).forEach(Reservation::expire);

            boolean hasActiveHold = reservationRepository.findActiveHold(
                    seatId,
                    ReservationStatus.PENDING_PAYMENT,
                    now).isPresent();
            if (!hasActiveHold && seat.getStatus() == SeatStatus.HELD) {
                seat.release();
            }
        }
    }
}
