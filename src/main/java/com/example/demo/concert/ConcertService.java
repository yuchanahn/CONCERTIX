package com.example.demo.concert;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.seat.SeatRepository;

@Service
public class ConcertService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.ASC, "id"));

    private final ConcertRepository repo;
    private final SeatRepository seatRepository;

    public ConcertService(ConcertRepository repo, SeatRepository seatRepository) {
        this.repo = repo;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public ConcertDtos.Response create(ConcertDtos.Request request) {
        validateRequest(request);
        Concert concert = new Concert(
                request.title(),
                request.description(),
                request.imageUrl(),
                request.venue(),
                request.holdSeconds(),
                request.maxTicketsPerUser());
        concert.updateDraft(
                request.title(),
                request.description(),
                request.imageUrl(),
                request.venue(),
                request.holdSeconds(),
                request.maxTicketsPerUser(),
                toSessions(request));
        return out(repo.save(concert));
    }

    @Transactional
    public ConcertDtos.Response update(UUID id, ConcertDtos.Request request) {
        validateRequest(request);
        Concert concert = repo.findForUpdateById(id).orElseThrow(ConcertService::notFound);
        if (seatRepository.existsBySession_Concert_Id(id)) {
            throw conflict();
        }
        concert.updateDraft(
                request.title(),
                request.description(),
                request.imageUrl(),
                request.venue(),
                request.holdSeconds(),
                request.maxTicketsPerUser(),
                toSessions(request));
        return out(concert);
    }

    @Transactional
    public void delete(UUID id) {
        Concert concert = repo.findForUpdateById(id).orElseThrow(ConcertService::notFound);
        concert.requireDraft();
        repo.delete(concert);
    }

    @Transactional(readOnly = true)
    public ConcertDtos.Response detail(UUID id, boolean publicOnly) {
        Concert concert = repo.findWithSessionsById(id).orElseThrow(ConcertService::notFound);
        if (publicOnly && concert.getStatus() != ConcertStatus.PUBLISHED) {
            throw notFound();
        }
        return out(concert);
    }

    @Transactional(readOnly = true)
    public ConcertDtos.PageResponse list(boolean publicOnly, int page, int size) {
        validatePage(page, size);
        PageRequest pageRequest = PageRequest.of(page, size, LIST_SORT);
        Page<Concert> result = publicOnly
                ? repo.findByStatus(ConcertStatus.PUBLISHED, pageRequest)
                : repo.findAll(pageRequest);
        return new ConcertDtos.PageResponse(
                result.getContent().stream().map(this::summary).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public ConcertDtos.Response publish(UUID id) {
        Concert concert = repo.findForUpdateById(id).orElseThrow(ConcertService::notFound);
        concert.publish();
        return out(concert);
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest();
        }
    }

    private void validateRequest(ConcertDtos.Request request) {
        var startsAtValues = new HashSet<>();
        for (ConcertDtos.SessionRequest session : request.sessions()) {
            if (!session.bookingOpensAt().isBefore(session.bookingClosesAt())
                    || session.bookingClosesAt().isAfter(session.startsAt())) {
                throw badRequest();
            }
            if (!startsAtValues.add(session.startsAt())) {
                throw badRequest();
            }
            if (request.maxTicketsPerUser() > session.capacity()) {
                throw badRequest();
            }
        }
    }

    private List<ConcertSession> toSessions(ConcertDtos.Request request) {
        return request.sessions().stream()
                .map(session -> new ConcertSession(
                        session.startsAt(),
                        session.bookingOpensAt(),
                        session.bookingClosesAt(),
                        session.capacity()))
                .toList();
    }

    private ConcertDtos.Response out(Concert concert) {
        return new ConcertDtos.Response(
                concert.getId(),
                concert.getTitle(),
                concert.getDescription(),
                concert.getImageUrl(),
                concert.getVenue(),
                concert.getHoldSeconds(),
                concert.getMaxTicketsPerUser(),
                concert.getStatus().name(),
                concert.getCreatedAt(),
                concert.getSessions().stream()
                        .map(session -> new ConcertDtos.SessionResponse(
                                session.getId(),
                                session.getStartsAt(),
                                session.getBookingOpensAt(),
                                session.getBookingClosesAt(),
                                session.getCapacity()))
                        .toList());
    }

    private ConcertDtos.Summary summary(Concert concert) {
        return new ConcertDtos.Summary(
                concert.getId(),
                concert.getTitle(),
                concert.getImageUrl(),
                concert.getVenue(),
                concert.getStatus().name(),
                concert.getCreatedAt());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT);
    }
}
