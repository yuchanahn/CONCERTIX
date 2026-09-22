package com.example.demo.concert;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "concert_sessions")
public class ConcertSession {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concert_id", nullable = false)
    private Concert concert;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "booking_opens_at", nullable = false)
    private Instant bookingOpensAt;

    @Column(name = "booking_closes_at", nullable = false)
    private Instant bookingClosesAt;

    @Column(nullable = false)
    private int capacity;

    protected ConcertSession() {
    }

    public ConcertSession(Instant startsAt, Instant bookingOpensAt, Instant bookingClosesAt, int capacity) {
        this.id = UUID.randomUUID();
        this.startsAt = startsAt;
        this.bookingOpensAt = bookingOpensAt;
        this.bookingClosesAt = bookingClosesAt;
        this.capacity = capacity;
    }

    void attachTo(Concert concert) {
        this.concert = concert;
    }

    public UUID getId() {
        return id;
    }

    public Concert getConcert() {
        return concert;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getBookingOpensAt() {
        return bookingOpensAt;
    }

    public Instant getBookingClosesAt() {
        return bookingClosesAt;
    }

    public int getCapacity() {
        return capacity;
    }
}
