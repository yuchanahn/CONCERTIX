package com.example.demo.reservation;

import java.time.Instant;
import java.util.UUID;

import com.example.demo.concert.ConcertSession;
import com.example.demo.seat.Seat;
import com.example.demo.user.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ConcertSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "held_until", nullable = false)
    private Instant heldUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected Reservation() {
    }

    public Reservation(Member member, ConcertSession session, Seat seat, long price, Instant heldUntil) {
        this.id = UUID.randomUUID();
        this.member = member;
        this.session = session;
        this.seat = seat;
        this.price = price;
        this.status = ReservationStatus.PENDING_PAYMENT;
        this.heldUntil = heldUntil;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public ConcertSession getSession() {
        return session;
    }

    public Seat getSeat() {
        return seat;
    }

    public long getPrice() {
        return price;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getHeldUntil() {
        return heldUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isExpired(Instant now) {
        return status == ReservationStatus.PENDING_PAYMENT && heldUntil.isBefore(now);
    }

    public void cancel() {
        if (status != ReservationStatus.PENDING_PAYMENT && status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("Only pending or confirmed reservations can be canceled");
        }
        this.status = ReservationStatus.CANCELED;
        this.updatedAt = Instant.now();
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public void confirm() {
        if (status != ReservationStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Only pending reservations can be confirmed");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }
}
