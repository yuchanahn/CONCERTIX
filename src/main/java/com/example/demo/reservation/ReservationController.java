package com.example.demo.reservation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.auth.JwtTokenProvider.AccessPrincipal;
import com.example.demo.reservation.ReservationDtos.CreateReservationRequest;
import com.example.demo.reservation.ReservationDtos.ReservationResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @AuthenticationPrincipal AccessPrincipal principal,
            @RequestHeader(value = "X-Queue-Token", required = false) String queueToken,
            @Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response = reservationService.holdAndReserve(principal.memberId(), request, queueToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<ReservationResponse>> getMyReservations(
            @AuthenticationPrincipal AccessPrincipal principal) {
        return ResponseEntity.ok(reservationService.getMyReservations(principal.memberId()));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> getReservation(
            @AuthenticationPrincipal AccessPrincipal principal,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(reservationService.getReservation(principal.memberId(), reservationId));
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(
            @AuthenticationPrincipal AccessPrincipal principal,
            @PathVariable UUID reservationId) {
        return ResponseEntity.ok(reservationService.cancelReservation(principal.memberId(), reservationId));
    }
}
