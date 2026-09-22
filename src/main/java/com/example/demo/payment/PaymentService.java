package com.example.demo.payment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.payment.PaymentDtos.CompleteRequest;
import com.example.demo.payment.PaymentDtos.CreateRequest;
import com.example.demo.payment.PaymentDtos.Response;
import com.example.demo.payment.PaymentExceptions.PaymentAccessDeniedException;
import com.example.demo.payment.PaymentExceptions.PaymentConflictException;
import com.example.demo.payment.PaymentExceptions.PaymentNotFoundException;
import com.example.demo.reservation.Reservation;
import com.example.demo.reservation.ReservationRepository;
import com.example.demo.reservation.ReservationStatus;
import com.example.demo.seat.Seat;
import com.example.demo.seat.SeatRepository;
import com.example.demo.seat.SeatStatus;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    public PaymentService(
            PaymentRepository paymentRepository,
            ReservationRepository reservationRepository,
            SeatRepository seatRepository) {
        this.paymentRepository = paymentRepository;
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public Response create(UUID memberId, CreateRequest request) {
        UUID seatId = reservationRepository.findSeatIdById(request.reservationId())
                .orElseThrow(PaymentNotFoundException::new);
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(PaymentNotFoundException::new);
        Reservation reservation = reservationRepository.findForUpdateById(request.reservationId())
                .orElseThrow(PaymentNotFoundException::new);
        requireOwner(reservation, memberId);

        Optional<Payment> existing = paymentRepository.findByReservation_Id(reservation.getId());
        if (existing.isPresent()) {
            return Response.from(existing.get());
        }

        Instant now = Instant.now();
        requirePayableReservation(reservation, seat, now);

        return Response.from(paymentRepository.save(new Payment(reservation)));
    }

    @Transactional(readOnly = true)
    public Response get(UUID memberId, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        requireOwner(payment.getReservation(), memberId);
        return Response.from(payment);
    }

    @Transactional
    public Response complete(UUID memberId, UUID paymentId, CompleteRequest request) {
        LockedPayment locked = lockPaymentGraph(paymentId);
        Payment payment = locked.payment();
        Reservation reservation = locked.reservation();
        Seat seat = locked.seat();
        requireOwner(reservation, memberId);

        if (request.result() == PaymentResult.SUCCESS && payment.getStatus() == PaymentStatus.SUCCESS) {
            return Response.from(payment);
        }
        if (request.result() == PaymentResult.FAILED && payment.getStatus() == PaymentStatus.FAILED) {
            return Response.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentConflictException("Payment was already completed with a different result");
        }

        if (request.result() == PaymentResult.SUCCESS) {
            Instant now = Instant.now();
            requirePayableReservation(reservation, seat, now);
            reservation.confirm();
            seat.confirm();
            payment.succeed();
        } else {
            failReservationAndReleaseSeat(reservation, seat, Instant.now());
            payment.fail();
        }

        return Response.from(payment);
    }

    @Transactional
    public Response cancel(UUID memberId, UUID paymentId) {
        LockedPayment locked = lockPaymentGraph(paymentId);
        Payment payment = locked.payment();
        Reservation reservation = locked.reservation();
        Seat seat = locked.seat();
        requireOwner(reservation, memberId);

        if (payment.getStatus() == PaymentStatus.CANCELED) {
            return Response.from(payment);
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            throw new PaymentConflictException("Failed payment cannot be canceled");
        }

        releaseReservationForCancellation(reservation, seat, Instant.now());
        payment.cancel();
        return Response.from(payment);
    }

    private LockedPayment lockPaymentGraph(UUID paymentId) {
        UUID reservationId = paymentRepository.findReservationIdById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        UUID seatId = paymentRepository.findSeatIdById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);

        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(PaymentNotFoundException::new);
        Reservation reservation = reservationRepository.findForUpdateById(reservationId)
                .orElseThrow(PaymentNotFoundException::new);
        Payment payment = paymentRepository.findForUpdateById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        return new LockedPayment(payment, reservation, seat);
    }

    private void requirePayableReservation(Reservation reservation, Seat seat, Instant now) {
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT || reservation.isExpired(now)) {
            throw new PaymentConflictException("Reservation is not payable");
        }
        if (seat.getStatus() != SeatStatus.HELD) {
            throw new PaymentConflictException("Seat is not held");
        }

        Optional<Reservation> activeHold = reservationRepository.findActiveHold(
                seat.getId(),
                ReservationStatus.PENDING_PAYMENT,
                now);
        if (activeHold.isEmpty() || !activeHold.get().getId().equals(reservation.getId())) {
            throw new PaymentConflictException("Reservation no longer owns the seat hold");
        }
    }

    private void failReservationAndReleaseSeat(Reservation reservation, Seat seat, Instant now) {
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new PaymentConflictException("Confirmed reservation cannot fail payment");
        }
        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
            boolean expired = reservation.isExpired(now);
            if (expired) {
                reservation.expire();
            } else {
                reservation.cancel();
            }
            releaseHeldSeatIfOwned(reservation, seat, now);
        }
    }

    private void releaseReservationForCancellation(Reservation reservation, Seat seat, Instant now) {
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            reservation.cancel();
            if (seat.getStatus() == SeatStatus.RESERVED) {
                seat.release();
            }
            return;
        }
        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
            if (reservation.isExpired(now)) {
                reservation.expire();
            } else {
                reservation.cancel();
            }
            releaseHeldSeatIfOwned(reservation, seat, now);
        }
    }

    private void releaseHeldSeatIfOwned(Reservation reservation, Seat seat, Instant now) {
        if (seat.getStatus() != SeatStatus.HELD) {
            return;
        }
        Optional<Reservation> activeHold = reservationRepository.findActiveHold(
                seat.getId(),
                ReservationStatus.PENDING_PAYMENT,
                now);
        if (activeHold.isEmpty() || activeHold.get().getId().equals(reservation.getId())) {
            seat.release();
        }
    }

    private void requireOwner(Reservation reservation, UUID memberId) {
        if (!reservation.getMember().getId().equals(memberId)) {
            throw new PaymentAccessDeniedException();
        }
    }

    private record LockedPayment(Payment payment, Reservation reservation, Seat seat) {
    }
}
