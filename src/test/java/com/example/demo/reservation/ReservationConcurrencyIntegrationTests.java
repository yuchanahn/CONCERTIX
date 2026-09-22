package com.example.demo.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.example.demo.TestInfrastructure;
import com.example.demo.auth.JwtTokenProvider;
import com.example.demo.concert.ConcertRepository;
import com.example.demo.seat.Seat;
import com.example.demo.seat.SeatRepository;
import com.example.demo.seat.SeatStatus;
import com.example.demo.user.Member;
import com.example.demo.user.MemberRepository;
import com.example.demo.user.MemberRole;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationConcurrencyIntegrationTests extends TestInfrastructure {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @LocalServerPort
    private int port;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final HttpClient http = HttpClient.newHttpClient();

    private String adminToken;

    @BeforeEach
    void setup() {
        cleanData();
        adminToken = createToken(MemberRole.ADMIN);
    }

    @org.junit.jupiter.api.AfterEach
    void teardown() {
        cleanData();
    }

    private void cleanData() {
        reservationRepository.deleteAll();
        concertRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void exactlyOneUserWinsWhenMultipleUsersConcurrentlyHoldSameSeat() throws Exception {
        int concurrencyLevel = 20;

        // 1. Prepare concert draft, create single seat while in PREPARING, then publish
        String concertId = createConcertDraft(10, 5, 300);
        String sessionId = getFirstSessionId(concertId);
        String seatId = createSingleSeat(concertId, sessionId);
        send("POST", "/api/admin/concerts/" + concertId + "/publish", adminToken, null);

        // 2. Prepare distinct user tokens
        List<String> userTokens = new ArrayList<>();
        for (int i = 0; i < concurrencyLevel; i++) {
            userTokens.add(createToken(MemberRole.USER));
        }

        // 3. Prepare concurrent execution
        ExecutorService executor = Executors.newFixedThreadPool(concurrencyLevel);
        CountDownLatch readyLatch = new CountDownLatch(concurrencyLevel);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrencyLevel);

        ConcurrentLinkedQueue<Integer> statusCodes = new ConcurrentLinkedQueue<>();
        String payload = String.format("{\"sessionId\":\"%s\",\"seatId\":\"%s\"}", sessionId, seatId);

        for (String userToken : userTokens) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // wait for simultaneous trigger
                    HttpResponse<String> res = send("POST", "/api/reservations", userToken, payload);
                    statusCodes.add(res.statusCode());
                } catch (Exception e) {
                    statusCodes.add(500);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // FIRE!
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(statusCodes).hasSize(concurrencyLevel);

        long successCount = statusCodes.stream().filter(code -> code == 201).count();
        long conflictCount = statusCodes.stream().filter(code -> code == 409).count();
        long errorCount = statusCodes.stream().filter(code -> code != 201 && code != 409).count();

        // 4. Assert: Exactly 1 success, 19 conflicts, 0 unexpected errors
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(concurrencyLevel - 1);
        assertThat(errorCount).isEqualTo(0);

        // 5. Assert: DB data integrity
        assertThat(reservationRepository.count()).isEqualTo(1);
        Seat seat = seatRepository.findById(java.util.UUID.fromString(seatId)).orElseThrow();
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void oneUserCannotExceedTicketLimitByHoldingDifferentSeatsConcurrently() throws Exception {
        String concertId = createConcertDraft(10, 1, 300);
        String sessionId = getFirstSessionId(concertId);
        List<String> seatIds = createSeats(concertId, sessionId, 2);
        send("POST", "/api/admin/concerts/" + concertId + "/publish", adminToken, null);

        String userToken = createToken(MemberRole.USER);
        List<String> payloads = seatIds.stream()
                .map(seatId -> String.format("{\"sessionId\":\"%s\",\"seatId\":\"%s\"}", sessionId, seatId))
                .toList();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        ConcurrentLinkedQueue<Integer> statusCodes = new ConcurrentLinkedQueue<>();

        for (String payload : payloads) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    statusCodes.add(send("POST", "/api/reservations", userToken, payload).statusCode());
                } catch (Exception exception) {
                    statusCodes.add(500);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        assertThat(doneLatch.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(statusCodes).containsExactlyInAnyOrder(201, 409);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    private String createToken(MemberRole role) {
        int seq = SEQUENCE.incrementAndGet();
        Member member = new Member("user" + seq + "@test.com", "hash", "User" + seq, role);
        memberRepository.save(member);
        return jwtTokenProvider.createAccessToken(member);
    }

    private String createConcertDraft(int capacity, int maxTickets, int holdSeconds) throws Exception {
        Instant now = Instant.now();
        Instant startsAt = now.plusSeconds(86400);
        Instant opensAt = now.minusSeconds(3600);
        Instant closesAt = now.plusSeconds(3600);

        String payload = String.format("""
                {
                    "title": "Concert %d",
                    "description": "Desc",
                    "imageUrl": "https://example.com/img.jpg",
                    "venue": "Venue",
                    "holdSeconds": %d,
                    "maxTicketsPerUser": %d,
                    "sessions": [
                        {
                            "startsAt": "%s",
                            "bookingOpensAt": "%s",
                            "bookingClosesAt": "%s",
                            "capacity": %d
                        }
                    ]
                }
                """, SEQUENCE.incrementAndGet(), holdSeconds, maxTickets, startsAt, opensAt, closesAt, capacity);

        HttpResponse<String> res = send("POST", "/api/admin/concerts", adminToken, payload);
        return extractField(res.body(), "id");
    }

    private String getFirstSessionId(String concertId) throws Exception {
        HttpResponse<String> res = send("GET", "/api/admin/concerts/" + concertId, adminToken, null);
        Matcher matcher = Pattern.compile("\"sessions\":\\[\\{\"id\":\"([^\"]+)\"").matcher(res.body());
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Session ID not found");
    }

    private String createSingleSeat(String concertId, String sessionId) throws Exception {
        String payload = """
                {
                    "section": "VIP",
                    "row": "A",
                    "startNumber": 1,
                    "count": 1,
                    "price": 100000
                }
                """;
        HttpResponse<String> res = send("POST", "/api/admin/concerts/" + concertId + "/sessions/" + sessionId + "/seats", adminToken, payload);
        Matcher matcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(res.body());
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Seat ID not found");
    }

    private List<String> createSeats(String concertId, String sessionId, int count) throws Exception {
        String payload = String.format("""
                {
                    "section": "VIP",
                    "row": "A",
                    "startNumber": 1,
                    "count": %d,
                    "price": 100000
                }
                """, count);
        HttpResponse<String> response = send(
                "POST",
                "/api/admin/concerts/" + concertId + "/sessions/" + sessionId + "/seats",
                adminToken,
                payload);
        Matcher matcher = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"").matcher(response.body());
        List<String> seatIds = new ArrayList<>();
        while (matcher.find()) {
            seatIds.add(matcher.group(1));
        }
        if (seatIds.size() != count) {
            throw new IllegalStateException("Expected " + count + " seat IDs but got " + seatIds.size());
        }
        return seatIds;
    }

    private String extractField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Field " + field + " not found");
    }

    private HttpResponse<String> send(String method, String path, String bearerToken, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path));

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
