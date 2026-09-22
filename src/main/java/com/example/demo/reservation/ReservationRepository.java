package com.example.demo.reservation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByMember_IdOrderByCreatedAtDesc(UUID memberId);

    @Query("select r from Reservation r join fetch r.seat s join fetch r.session sess where r.id = :id")
    Optional<Reservation> findWithDetailsById(@Param("id") UUID id);

    @Query("select r.seat.id from Reservation r where r.id = :id")
    Optional<UUID> findSeatIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findForUpdateById(@Param("id") UUID id);

    @Query("select r from Reservation r where r.seat.id = :seatId and r.status = :status and r.heldUntil > :now")
    Optional<Reservation> findActiveHold(@Param("seatId") UUID seatId, @Param("status") ReservationStatus status, @Param("now") Instant now);

    @Query("select r from Reservation r where r.seat.id = :seatId and r.status = :status and r.heldUntil <= :now")
    List<Reservation> findExpiredHolds(
            @Param("seatId") UUID seatId,
            @Param("status") ReservationStatus status,
            @Param("now") Instant now);

    @Query("""
            select distinct r.seat.id
            from Reservation r
            where r.session.id = :sessionId
              and r.status = :status
              and r.heldUntil <= :now
            """)
    List<UUID> findSeatIdsWithExpiredHolds(
            @Param("sessionId") UUID sessionId,
            @Param("status") ReservationStatus status,
            @Param("now") Instant now);

    @Query("""
            select count(r)
            from Reservation r
            where r.member.id = :memberId
              and r.session.concert.id = :concertId
              and (
                    r.status = :confirmedStatus
                    or (r.status = :pendingStatus and r.heldUntil > :now)
              )
            """)
    long countActiveReservationsByMemberAndConcert(
            @Param("memberId") UUID memberId,
            @Param("concertId") UUID concertId,
            @Param("pendingStatus") ReservationStatus pendingStatus,
            @Param("confirmedStatus") ReservationStatus confirmedStatus,
            @Param("now") Instant now);
}
