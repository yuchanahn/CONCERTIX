package com.example.demo.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByReservation_Id(UUID reservationId);

    @Query("select p.reservation.id from Payment p where p.id = :id")
    Optional<UUID> findReservationIdById(@Param("id") UUID id);

    @Query("select p.reservation.seat.id from Payment p where p.id = :id")
    Optional<UUID> findSeatIdById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findForUpdateById(@Param("id") UUID id);
}
