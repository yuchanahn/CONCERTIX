package com.example.demo.concert;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class ConcertController {

    private final ConcertService service;

    public ConcertController(ConcertService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/concerts")
    @ResponseStatus(HttpStatus.CREATED)
    public ConcertDtos.Response create(@Valid @RequestBody ConcertDtos.Request request) {
        return service.create(request);
    }

    @GetMapping("/api/admin/concerts")
    public ConcertDtos.PageResponse adminList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(false, page, size);
    }

    @GetMapping("/api/admin/concerts/{id}")
    public ConcertDtos.Response adminDetail(@PathVariable UUID id) {
        return service.detail(id, false);
    }

    @PutMapping("/api/admin/concerts/{id}")
    public ConcertDtos.Response update(@PathVariable UUID id, @Valid @RequestBody ConcertDtos.Request request) {
        return service.update(id, request);
    }

    @DeleteMapping("/api/admin/concerts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/api/admin/concerts/{id}/publish")
    public ConcertDtos.Response publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @GetMapping("/api/concerts")
    public ConcertDtos.PageResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(true, page, size);
    }

    @GetMapping("/api/concerts/{id}")
    public ConcertDtos.Response publicDetail(@PathVariable UUID id) {
        return service.detail(id, true);
    }
}
