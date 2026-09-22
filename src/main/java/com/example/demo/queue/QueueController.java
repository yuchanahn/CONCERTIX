package com.example.demo.queue;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.auth.JwtTokenProvider.AccessPrincipal;
import com.example.demo.queue.QueueDtos.QueueStatusResponse;

@RestController
@RequestMapping("/api/queues")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/{concertId}/join")
    public QueueStatusResponse join(
            @AuthenticationPrincipal AccessPrincipal principal,
            @PathVariable UUID concertId) {
        return queueService.join(concertId, principal.memberId());
    }

    @GetMapping("/{concertId}/me")
    public QueueStatusResponse status(
            @AuthenticationPrincipal AccessPrincipal principal,
            @PathVariable UUID concertId) {
        return queueService.status(concertId, principal.memberId());
    }
}
