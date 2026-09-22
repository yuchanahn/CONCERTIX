package com.example.demo.queue;

import java.time.Instant;

public final class QueueDtos {

    private QueueDtos() {
    }

    public record QueueStatusResponse(
            boolean active,
            boolean admitted,
            Long position,
            String admissionToken,
            Instant admissionExpiresAt) {
    }
}
