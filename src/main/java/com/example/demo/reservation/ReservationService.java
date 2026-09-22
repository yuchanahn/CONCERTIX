package com.example.demo.reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.concert.Concert;
import com.example.demo.concert.ConcertSession;
import com.example.demo.concert.ConcertStatus;
import com.example.demo.payment.PaymentRepository;
import com.example.demo.payment.PaymentStatus;
import com.example.demo.queue.QueueService;
import com.example.demo.reservation.ReservationDtos.CreateReservationRequest;
import com.example.demo.reservation.ReservationDtos.ReservationResponse;
import com.example.demo.reservation.ReservationExceptions.BookingPeriodClosedException;
import com.example.demo.reservation.ReservationExceptions.ConcertNotPublishedException;
import com.example.demo.reservation.ReservationExceptions.MaxTicketsExceededException;
import com.example.demo.reservation.ReservationExceptions.ReservationAccessDeniedException;
import com.example.demo.reservation.ReservationExceptions.ReservationAlreadyCanceledException;
import com.example.demo.reservation.ReservationExceptions.ReservationNotFoundException;
import com.example.demo.reservation.ReservationExceptions.SeatNotAvailableException;
import com.example.demo.seat.Seat;
import com.example.demo.seat.SeatRepository;
import com.example.demo.seat.SeatStatus;
import com.example.demo.user.Member;
import com.example.demo.user.MemberRepository;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final QueueService queueService;

    public ReservationService(
            ReservationRepository reservationRepository,
            SeatRepository seatRepository,
            MemberRepository memberRepository,
            PaymentRepository paymentRepository,
            QueueService queueService) {
        this.reservationRepository = reservationRepository;
        this.seatRepository = seatRepository;
        this.memberRepository = memberRepository;
        this.paymentRepository = paymentRepository;
        this.queueService = queueService;
    }

    @Transactional
    public ReservationResponse holdAndReserve(UUID memberId, CreateReservationRequest request, String queueToken) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        // Acquire pessimistic write lock on the seat
        Seat seat = seatRepository.findByIdForUpdate(request.seatId())
                .orElseThrow(() -> new SeatNotAvailableException("Seat not found"));

        // Validate session match
        if (!seat.getSession().getId().equals(request.sessionId())) {
            throw new SeatNotAvailableException("Seat does not belong to session");
        }

        ConcertSession session = seat.getSession();
        Concert concert = session.getConcert();

        queueService.validateAdmission(concert.getId(), memberId, queueToken);

        // Validate concert status
        if (concert.getStatus() != ConcertStatus.PUBLISHED) {
            throw new ConcertNotPublishedException();
        }

        // Validate booking period
        Instant now = Instant.now();
        if (now.isBefore(session.getBookingOpensAt()) || now.isAfter(session.getBookingClosesAt())) {
            throw new BookingPeriodClosedException("Booking period is closed");
        }

        // Validate max tickets per user
        long activeCount = reservationRepository.countActiveReservationsByMemberAndConcert(
                memberId,
                concert.getId(),
                ReservationStatus.PENDING_PAYMENT,
                ReservationStatus.CONFIRMED,
                now);
        if (activeCount >= concert.getMaxTicketsPerUser()) {
            throw new MaxTicketsExceededException("Maximum ticket limit reached for this concert");
        }

        // Validate seat availability
        SeatStatus seatStatus = seat.getStatus();
        if (seatStatus == SeatStatus.DISABLED) {
            throw new SeatNotAvailableException("Seat is disabled");
        }
        if (seatStatus == SeatStatus.RESERVED) {
            throw new SeatNotAvailableException("Seat is already reserved");
        }
        if (seatStatus == SeatStatus.HELD) {
            Optional<Reservation> activeHold = reservationRepository.findActiveHold(
                    seat.getId(), ReservationStatus.PENDING_PAYMENT, now);
            if (activeHold.isPresent()) {
                throw new SeatNotAvailableException("Seat is currently held by another user");
            }

            reservationRepository.findExpiredHolds(
                    seat.getId(),
                    ReservationStatus.PENDING_PAYMENT,
                    now).forEach(Reservation::expire);
        }

        // Apply hold
        seat.hold();
        Instant heldUntil = now.plusSeconds(concert.getHoldSeconds());
        Reservation reservation = new Reservation(member, session, seat, seat.getPrice(), heldUntil);
        Reservation saved = reservationRepository.save(reservation);

        return ReservationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyReservations(UUID memberId) {
        return reservationRepository.findByMember_IdOrderByCreatedAtDesc(memberId)
                .stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(UUID memberId, UUID reservationId) {
        Reservation reservation = reservationRepository.findWithDetailsById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        if (!reservation.getMember().getId().equals(memberId)) {
            throw new ReservationAccessDeniedException();
        }

        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse cancelReservation(UUID memberId, UUID reservationId) {
        Reservation reservation = reservationRepository.findWithDetailsById(reservationId)
                .orElseThrow(ReservationNotFoundException::new);

        if (!reservation.getMember().getId().equals(memberId)) {
            throw new ReservationAccessDeniedException();
        }

        Seat seat = seatRepository.findByIdForUpdate(reservation.getSeat().getId())
                .orElseThrow(() -> new IllegalStateException("Seat not found for reservation"));

        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new ReservationAlreadyCanceledException("Reservation already canceled");
        }
        if (reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new ReservationAlreadyCanceledException("Reservation already expired");
        }

        Instant now = Instant.now();
        if (reservation.isExpired(now)) {
            throw new ReservationAlreadyCanceledException("Reservation already expired");
        }

        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
            Optional<Reservation> activeHold = reservationRepository.findActiveHold(
                    seat.getId(),
                    ReservationStatus.PENDING_PAYMENT,
                    now);
            if (activeHold.isEmpty() || !activeHold.get().getId().equals(reservation.getId())) {
                throw new ReservationAlreadyCanceledException("Reservation no longer owns the seat hold");
            }
        }

        reservation.cancel();
        seat.release();
        paymentRepository.findByReservation_Id(reservation.getId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.SUCCESS) {
                payment.cancel();
            }
        });

        return ReservationResponse.from(reservation);
    }
}
