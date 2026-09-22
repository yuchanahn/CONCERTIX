package com.example.demo.seat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findBySession_IdOrderBySectionAscRowLabelAscSeatNumberAsc(UUID sessionId);

    Optional<Seat> findByIdAndSession_Id(UUID id, UUID sessionId);

    boolean existsBySession_Concert_Id(UUID concertId);

    long countBySession_Id(UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s join fetch s.session sess join fetch sess.concert where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") UUID id);
}
