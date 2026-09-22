package com.example.demo.seat;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class SeatController {

    private final SeatService service;

    public SeatController(SeatService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/concerts/{concertId}/sessions/{sessionId}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SeatDtos.Response> create(
            @PathVariable UUID concertId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody SeatDtos.CreateRequest request) {
        return service.create(concertId, sessionId, request);
    }

    @GetMapping("/api/admin/concerts/{concertId}/sessions/{sessionId}/seats")
    public List<SeatDtos.Response> adminList(@PathVariable UUID concertId, @PathVariable UUID sessionId) {
        return service.list(concertId, sessionId, false);
    }

    @PatchMapping("/api/admin/concerts/{concertId}/sessions/{sessionId}/seats/{seatId}")
    public SeatDtos.Response updateStatus(
            @PathVariable UUID concertId,
            @PathVariable UUID sessionId,
            @PathVariable UUID seatId,
            @Valid @RequestBody SeatDtos.StatusRequest request) {
        return service.updateStatus(concertId, sessionId, seatId, request);
    }

    @GetMapping("/api/concerts/{concertId}/sessions/{sessionId}/seats")
    public List<SeatDtos.Response> publicList(@PathVariable UUID concertId, @PathVariable UUID sessionId) {
        return service.list(concertId, sessionId, true);
    }
}

