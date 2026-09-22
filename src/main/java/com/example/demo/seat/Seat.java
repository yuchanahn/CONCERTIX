package com.example.demo.seat;

import java.util.UUID;

import com.example.demo.concert.ConcertSession;

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
@Table(name = "seats")
public class Seat {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ConcertSession session;

    @Column(nullable = false, length = 50)
    private String section;

    @Column(name = "row_label", nullable = false, length = 20)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Version
    @Column(nullable = false)
    private int version;

    protected Seat() {
    }

    public Seat(ConcertSession session, String section, String rowLabel, int seatNumber, long price) {
        this.id = UUID.randomUUID();
        this.session = session;
        this.section = section;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return session.getId();
    }

    public String getSection() {
        return section;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public long getPrice() {
        return price;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public ConcertSession getSession() {
        return session;
    }

    public void hold() {
        this.status = SeatStatus.HELD;
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
    }

    public void confirm() {
        this.status = SeatStatus.RESERVED;
    }

    public void setAdminStatus(SeatStatus status) {
        if (status != SeatStatus.AVAILABLE && status != SeatStatus.DISABLED) {
            throw new IllegalArgumentException("admin status must be AVAILABLE or DISABLED");
        }
        this.status = status;
    }
}

