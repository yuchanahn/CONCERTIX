package com.example.demo.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
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
import com.example.demo.seat.SeatRepository;
import com.example.demo.seat.SeatStatus;
import com.example.demo.user.Member;
import com.example.demo.user.MemberRepository;
import com.example.demo.user.MemberRole;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationApiIntegrationTests extends TestInfrastructure {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @LocalServerPort
    private int port;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final HttpClient http = HttpClient.newHttpClient();

    private String adminToken;
    private String user1Token;
    private String user2Token;

    @BeforeEach
    void setup() {
        cleanData();
        adminToken = createToken(MemberRole.ADMIN);
        user1Token = createToken(MemberRole.USER);
        user2Token = createToken(MemberRole.USER);
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
    void userCanHoldAvailableSeatAndCancelIt() throws Exception {
        // 1. Create concert draft
        String concertId = createConcertDraft(10, 2, 300);

        // 2. Fetch session and create seats while in PREPARING
        String sessionId = getFirstSessionId(concertId);
        String seatId = createSeatsAndGetFirstSeatId(concertId, sessionId, 2);

        // 3. Publish the concert
        send("POST", "/api/admin/concerts/" + concertId + "/publish", adminToken, null);

        // 3. Anonymous user cannot reserve (401)
        String reservePayload = String.format("{\"sessionId\":\"%s\",\"seatId\":\"%s\"}", sessionId, seatId);
        HttpResponse<String> unauth = send("POST", "/api/reservations", null, reservePayload);
        assertThat(unauth.statusCode()).isEqualTo(401);

        // 4. User1 reserves the seat (201)
        HttpResponse<String> reserveRes = send("POST", "/api/reservations", user1Token, reservePayload);
        assertThat(reserveRes.statusCode()).isEqualTo(201);
        assertThat(reserveRes.body()).contains("\"status\":\"PENDING_PAYMENT\"");
        String reservationId = extractField(reserveRes.body(), "id");

        // 5. User2 tries to reserve the same held seat (409)
        HttpResponse<String> conflictRes = send("POST", "/api/reservations", user2Token, reservePayload);
        assertThat(conflictRes.statusCode()).isEqualTo(409);

        // 6. User1 checks their reservations list
        HttpResponse<String> myList = send("GET", "/api/reservations/me", user1Token, null);
        assertThat(myList.statusCode()).isEqualTo(200);
        assertThat(myList.body()).contains(reservationId);

        // 7. User2 tries to access User1's reservation (403)
        HttpResponse<String> forbidden = send("GET", "/api/reservations/" + reservationId, user2Token, null);
        assertThat(forbidden.statusCode()).isEqualTo(403);

        // 8. User1 checks reservation details (200)
        HttpResponse<String> detail = send("GET", "/api/reservations/" + reservationId, user1Token, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("\"status\":\"PENDING_PAYMENT\"");

        assertThat(detail.body()).contains("\"startTime\":");

        // 9. User1 cancels the reservation (200)
        HttpResponse<String> cancelRes = send("POST", "/api/reservations/" + reservationId + "/cancel", user1Token,
                null);
        assertThat(cancelRes.statusCode()).isEqualTo(200);
        assertThat(cancelRes.body()).contains("\"status\":\"CANCELED\"");

        // 10. Canceling again returns 409
        HttpResponse<String> cancelAgain = send("POST", "/api/reservations/" + reservationId + "/cancel", user1Token,
                null);
        assertThat(cancelAgain.statusCode()).isEqualTo(409);

        // 11. Now User2 can reserve the freed seat (201)
        HttpResponse<String> user2Reserve = send("POST", "/api/reservations", user2Token, reservePayload);
        assertThat(user2Reserve.statusCode()).isEqualTo(201);
    }

    @Test
    void cannotReserveUnpublishedConcertOrWhenMaxTicketsExceeded() throws Exception {
        // Create concert in PREPARING (unpublished)
        String concertId = createConcertDraft(5, 1, 300);
        String sessionId = getFirstSessionId(concertId);
        String seat1Id = createSeatsAndGetFirstSeatId(concertId, sessionId, 2);

        String payload1 = String.format("{\"sessionId\":\"%s\",\"seatId\":\"%s\"}", sessionId, seat1Id);

        // 1. Unpublished concert reservation returns 409
        HttpResponse<String> res1 = send("POST", "/api/reservations", user1Token, payload1);
        assertThat(res1.statusCode()).isEqualTo(409);

        // Publish concert
        send("POST", "/api/admin/concerts/" + concertId + "/publish", adminToken, null);

        // 2. Now user1 can reserve seat1 (201)
        HttpResponse<String> res2 = send("POST", "/api/reservations", user1Token, payload1);
        assertThat(res2.statusCode()).isEqualTo(201);

        // 3. User1 tries to reserve another seat, but maxTicketsPerUser is 1 (409)
        HttpResponse<String> seatsRes = send("GET",
                "/api/admin/concerts/" + concertId + "/sessions/" + sessionId + "/seats", adminToken, null);
        String seat2Id = extractSecondSeatId(seatsRes.body());
        String payload2 = String.format("{\"sessionId\":\"%s\",\"seatId\":\"%s\"}", sessionId, seat2Id);

        HttpResponse<String> maxExceeded = send("POST", "/api/reservations", user1Token, payload2);
        assertThat(maxExceeded.statusCode()).isEqualTo(409);
    }

    @Test
    void expiredHoldCanBeReclaimedByAnotherUser() throws Exception {
        // Hold seconds = 1
        String concertId = createConcertDraft(5, 2, 1);
        String sessionId = getFirstSessionId(concertId);
        String seatId = createSeatsAndGetFirstSeatId(concertId, sessionId, 1);
        send("POST", "/api/admin/concerts/" + concertId + "/publish", adminToken, null);

        String payload = String.format("{\"sessionId\":\"%s\",\"seatId\":\"%s\"}", sessionId, seatId);

        // User1 holds the seat
        HttpResponse<String> user1Res = send("POST", "/api/reservations", user1Token, payload);
        assertThat(user1Res.statusCode()).isEqualTo(201);
        String user1ReservationId = extractField(user1Res.body(), "id");

        // Immediate attempt by User2 fails with 409
        HttpResponse<String> immediateRes = send("POST", "/api/reservations", user2Token, payload);
        assertThat(immediateRes.statusCode()).isEqualTo(409);

        // Wait 1.5 seconds for hold expiration
        Thread.sleep(1500);

        // Public seat refresh lazily expires the old hold so the UI can select it
        // again.
        HttpResponse<String> refreshedSeats = send(
                "GET",
                "/api/concerts/" + concertId + "/sessions/" + sessionId + "/seats",
                null,
                null);
        assertThat(refreshedSeats.statusCode()).isEqualTo(200);
        assertThat(refreshedSeats.body()).contains("\"status\":\"AVAILABLE\"");
        assertThat(reservationRepository.findById(UUID.fromString(user1ReservationId)).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.EXPIRED);

        // User2 holds the expired seat successfully (201)
        HttpResponse<String> expiredReclaimed = send("POST", "/api/reservations", user2Token, payload);
        assertThat(expiredReclaimed.statusCode()).isEqualTo(201);
        String user2ReservationId = extractField(expiredReclaimed.body(), "id");

        // The old owner must not be able to release the seat now held by User2.
        HttpResponse<String> staleCancel = send(
                "POST",
                "/api/reservations/" + user1ReservationId + "/cancel",
                user1Token,
                null);
        assertThat(staleCancel.statusCode()).isEqualTo(409);
        assertThat(seatRepository.findById(UUID.fromString(seatId)).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.HELD);
        Reservation user2Reservation = reservationRepository.findById(UUID.fromString(user2ReservationId))
                .orElseThrow();
        assertThat(user2Reservation.getSeat().getId()).isEqualTo(UUID.fromString(seatId));
        assertThat(user2Reservation.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    void expiredHoldDoesNotCountTowardMaxTicketsPerUser() throws Exception {
        String concertId = createConcertDraft(5, 1, 1);
        String sessionId = getFirstSessionId(concertId);
        String seat1Id = createSeatsAndGetFirstSeatId(concertId, sessionId, 2);
        send("POST", "/api/admin/concerts/" + concertId + "/publish", adminToken, null);

        String seat1Payload = String.format(
                "{\"sessionId\":\"%s\",\"seatId\":\"%s\"}",
                sessionId,
                seat1Id);
        HttpResponse<String> firstHold = send("POST", "/api/reservations", user1Token, seat1Payload);
        assertThat(firstHold.statusCode()).isEqualTo(201);

        Thread.sleep(1500);

        HttpResponse<String> seatsRes = send(
                "GET",
                "/api/admin/concerts/" + concertId + "/sessions/" + sessionId + "/seats",
                adminToken,
                null);
        String seat2Id = extractSecondSeatId(seatsRes.body());
        String seat2Payload = String.format(
                "{\"sessionId\":\"%s\",\"seatId\":\"%s\"}",
                sessionId,
                seat2Id);

        HttpResponse<String> secondHold = send("POST", "/api/reservations", user1Token, seat2Payload);
        assertThat(secondHold.statusCode()).isEqualTo(201);
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

    private String createConcertAndPublish(int capacity, int maxTickets, int holdSeconds) throws Exception {
        String concertId = createConcertDraft(capacity, maxTickets, holdSeconds);
        send("POST", "/api/admin/concerts/" + concertId + "/publish", adminToken, null);
        return concertId;
    }

    private String getFirstSessionId(String concertId) throws Exception {
        HttpResponse<String> res = send("GET", "/api/admin/concerts/" + concertId, adminToken, null);
        Matcher matcher = Pattern.compile("\"sessions\":\\[\\{\"id\":\"([^\"]+)\"").matcher(res.body());
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Session ID not found in: " + res.body());
    }

    private String createSeatsAndGetFirstSeatId(String concertId, String sessionId, int count) throws Exception {
        String payload = String.format("""
                {
                    "section": "VIP",
                    "row": "A",
                    "startNumber": 1,
                    "count": %d,
                    "price": 50000
                }
                """, count);
        HttpResponse<String> res = send("POST",
                "/api/admin/concerts/" + concertId + "/sessions/" + sessionId + "/seats", adminToken, payload);
        Matcher matcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(res.body());
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Seat ID not found");
    }

    private String extractSecondSeatId(String json) {
        Matcher matcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(json);
        if (matcher.find() && matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Second seat ID not found");
    }

    private String extractField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Field " + field + " not found in: " + json);
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
