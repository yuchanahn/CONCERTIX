package com.example.demo.seat;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.concert.Concert;
import com.example.demo.concert.ConcertRepository;
import com.example.demo.concert.ConcertSession;
import com.example.demo.concert.ConcertStatus;
import com.example.demo.reservation.ReservationExpiryService;

@Service
public class SeatService {

    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;
    private final ReservationExpiryService reservationExpiryService;

    public SeatService(
            ConcertRepository concertRepository,
            SeatRepository seatRepository,
            ReservationExpiryService reservationExpiryService) {
        this.concertRepository = concertRepository;
        this.seatRepository = seatRepository;
        this.reservationExpiryService = reservationExpiryService;
    }

    @Transactional
    public List<SeatDtos.Response> create(UUID concertId, UUID sessionId, SeatDtos.CreateRequest request) {
        Concert concert = concertRepository.findForUpdateById(concertId).orElseThrow(SeatService::notFound);
        concert.requireDraft();
        ConcertSession session = findSession(concert, sessionId);

        long lastNumber = (long) request.startNumber() + request.count() - 1;
        if (lastNumber > 100000) {
            throw badRequest();
        }
        long existingCount = seatRepository.countBySession_Id(sessionId);
        if (existingCount + request.count() > session.getCapacity()) {
            throw conflict();
        }

        var existingKeys = new HashSet<String>();
        seatRepository.findBySession_IdOrderBySectionAscRowLabelAscSeatNumberAsc(sessionId)
                .forEach(seat -> existingKeys.add(key(seat.getSection(), seat.getRowLabel(), seat.getSeatNumber())));

        for (int number = request.startNumber(); number <= lastNumber; number++) {
            if (existingKeys.contains(key(request.section(), request.row(), number))) {
                throw conflict();
            }
        }

        List<Seat> seats = java.util.stream.IntStream.range(0, request.count())
                .mapToObj(offset -> new Seat(
                        session,
                        request.section(),
                        request.row(),
                        request.startNumber() + offset,
                        request.price()))
                .toList();
        return seatRepository.saveAll(seats).stream().map(this::out).toList();
    }

    @Transactional
    public List<SeatDtos.Response> list(UUID concertId, UUID sessionId, boolean publicOnly) {
        Concert concert = concertRepository.findWithSessionsById(concertId).orElseThrow(SeatService::notFound);
        if (publicOnly && concert.getStatus() != ConcertStatus.PUBLISHED) {
            throw notFound();
        }
        findSession(concert, sessionId);
        reservationExpiryService.expireHoldsForSession(sessionId);
        return seatRepository.findBySession_IdOrderBySectionAscRowLabelAscSeatNumberAsc(sessionId)
                .stream()
                .map(this::out)
                .toList();
    }

    @Transactional
    public SeatDtos.Response updateStatus(UUID concertId, UUID sessionId, UUID seatId, SeatDtos.StatusRequest request) {
        Concert concert = concertRepository.findForUpdateById(concertId).orElseThrow(SeatService::notFound);
        concert.requireDraft();
        findSession(concert, sessionId);
        Seat seat = seatRepository.findByIdAndSession_Id(seatId, sessionId).orElseThrow(SeatService::notFound);
        try {
            seat.setAdminStatus(request.status());
        } catch (IllegalArgumentException ex) {
            throw badRequest();
        }
        return out(seat);
    }

    private ConcertSession findSession(Concert concert, UUID sessionId) {
        return concert.getSessions().stream()
                .filter(session -> session.getId().equals(sessionId))
                .findFirst()
                .orElseThrow(SeatService::notFound);
    }

    private SeatDtos.Response out(Seat seat) {
        return new SeatDtos.Response(
                seat.getId(),
                seat.getSessionId(),
                seat.getSection(),
                seat.getRowLabel(),
                seat.getSeatNumber(),
                seat.getPrice(),
                seat.getStatus().name());
    }

    private static String key(String section, String row, int number) {
        return section + "\u0000" + row + "\u0000" + number;
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
