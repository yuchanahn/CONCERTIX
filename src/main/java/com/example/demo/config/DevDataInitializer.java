package com.example.demo.config;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.concert.Concert;
import com.example.demo.concert.ConcertRepository;
import com.example.demo.concert.ConcertSession;
import com.example.demo.seat.Seat;
import com.example.demo.seat.SeatRepository;
import com.example.demo.user.Member;
import com.example.demo.user.MemberRepository;
import com.example.demo.user.MemberRole;

@Component
public class DevDataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataInitializer(
            MemberRepository memberRepository,
            ConcertRepository concertRepository,
            SeatRepository seatRepository,
            PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.concertRepository = concertRepository;
        this.seatRepository = seatRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Seed default Admin and Users if not present
        if (memberRepository.findByEmailIgnoreCase("admin@concert.com").isEmpty()) {
            memberRepository.save(new Member("admin@concert.com", passwordEncoder.encode("admin1234"), "총괄관리자", MemberRole.ADMIN));
        }
        if (memberRepository.findByEmailIgnoreCase("user1@concert.com").isEmpty()) {
            memberRepository.save(new Member("user1@concert.com", passwordEncoder.encode("user1234"), "김예매", MemberRole.USER));
        }
        if (memberRepository.findByEmailIgnoreCase("user2@concert.com").isEmpty()) {
            memberRepository.save(new Member("user2@concert.com", passwordEncoder.encode("user1234"), "이티켓", MemberRole.USER));
        }

        // 2. Seed a sample concert if no concerts exist
        if (concertRepository.count() == 0) {
            Instant now = Instant.now();
            Instant startsAt = now.plusSeconds(86400 * 7); // 7 days later
            Instant opensAt = now.minusSeconds(3600); // already open
            Instant closesAt = startsAt.minusSeconds(3600); // closes 1h before start

            Concert concert = new Concert(
                    "2026 메가 라이브 월드 투어 (SEOUL)",
                    "국내 최대 규모의 라이브 콘서트! 최고의 사운드와 화려한 퍼포먼스를 경험하세요.",
                    "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&auto=format&fit=crop&q=60",
                    "올림픽공원 체조경기장 (KSPO DOME)",
                    300, // 5 min hold
                    4    // max 4 tickets per user
            );

            ConcertSession session = new ConcertSession(startsAt, opensAt, closesAt, 40);
            concert.updateDraft(
                    concert.getTitle(),
                    concert.getDescription(),
                    concert.getImageUrl(),
                    concert.getVenue(),
                    concert.getHoldSeconds(),
                    concert.getMaxTicketsPerUser(),
                    List.of(session)
            );

            Concert savedConcert = concertRepository.save(concert);
            ConcertSession savedSession = savedConcert.getSessions().get(0);

            // Pre-create VIP section seats (10 seats) and R section seats (10 seats)
            for (int i = 1; i <= 10; i++) {
                seatRepository.save(new Seat(savedSession, "VIP", "A", i, 150000));
            }
            for (int i = 1; i <= 10; i++) {
                seatRepository.save(new Seat(savedSession, "R", "B", i, 110000));
            }

            // Publish concert
            savedConcert.publish();
            concertRepository.save(savedConcert);
        }
    }
}
