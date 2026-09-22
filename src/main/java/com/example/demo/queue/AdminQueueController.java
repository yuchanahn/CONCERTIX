package com.example.demo.queue;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.queue.QueueDtos.QueueStatusResponse;

@RestController
@RequestMapping("/api/admin/queues")
public class AdminQueueController {

    private final QueueService queueService;

    public AdminQueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping("/{concertId}/activate")
    public QueueStatusResponse activate(@PathVariable UUID concertId) {
        return queueService.activate(concertId);
    }

    @PostMapping("/{concertId}/deactivate")
    public QueueStatusResponse deactivate(@PathVariable UUID concertId) {
        return queueService.deactivate(concertId);
    }
}
