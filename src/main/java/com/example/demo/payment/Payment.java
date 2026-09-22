package com.example.demo.payment;

import java.time.Instant;
import java.util.UUID;

import com.example.demo.reservation.Reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected Payment() {
    }

    public Payment(Reservation reservation) {
        this.id = UUID.randomUUID();
        this.reservation = reservation;
        this.amount = reservation.getPrice();
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Reservation getReservation() {
        return reservation;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void succeed() {
        requirePending();
        this.status = PaymentStatus.SUCCESS;
        this.updatedAt = Instant.now();
    }

    public void fail() {
        requirePending();
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (status == PaymentStatus.CANCELED) {
            return;
        }
        if (status == PaymentStatus.FAILED) {
            throw new IllegalStateException("Failed payment cannot be canceled");
        }
        this.status = PaymentStatus.CANCELED;
        this.updatedAt = Instant.now();
    }

    private void requirePending() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Payment is not pending");
        }
    }
}
