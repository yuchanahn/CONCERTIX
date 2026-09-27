package com.example.demo.concert;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ConcertRepository extends JpaRepository<Concert, UUID> {

    Page<Concert> findByStatus(ConcertStatus status, Pageable pageable);

    Page<Concert> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    @EntityGraph(attributePaths = "sessions")
    @Query("select c from Concert c where c.id = :id")
    Optional<Concert> findWithSessionsById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Concert c where c.id = :id")
    Optional<Concert> findForUpdateById(UUID id);
}
