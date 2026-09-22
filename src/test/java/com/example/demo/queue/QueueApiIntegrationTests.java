package com.example.demo.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.demo.TestInfrastructure;
import com.example.demo.auth.JwtTokenProvider;
import com.example.demo.concert.ConcertRepository;
import com.example.demo.reservation.ReservationRepository;
import com.example.demo.user.Member;
import com.example.demo.user.MemberRepository;
import com.example.demo.user.MemberRole;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.queue.max-admitted=1",
                "app.queue.admission-ttl=1s"
        })
class QueueApiIntegrationTests extends TestInfrastructure {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @LocalServerPort
    private int port;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private QueueAdmissionLock admissionLock;

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

    @AfterEach
    void teardown() {
        cleanData();
    }

    private void cleanData() {
        Set<String> queueKeys = redisTemplate.keys("queue:*");
        if (queueKeys != null && !queueKeys.isEmpty()) {
            redisTemplate.delete(queueKeys);
        }
        reservationRepository.deleteAll();
        concertRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void queueControlsAdmissionOrderAndBlocksExpiredOrMissingTokens() throws Exception {
        String concertId = createConcertDraft();
        String sessionId = getFirstSessionId(concertId);
        String[] seatIds = createSeats(concertId, sessionId);
        send("POST", "/api/admin/concerts/" + concertId + "/publish", adminToken, null, null);

        HttpResponse<String> activated = send(
                "POST",
                "/api/admin/queues/" + concertId + "/activate",
                adminToken,
                null,
                null);
        assertThat(activated.statusCode()).isEqualTo(200);

        HttpResponse<String> user1Join = send(
                "POST", "/api/queues/" + concertId + "/join", user1Token, null, null);
        assertThat(user1Join.statusCode()).isEqualTo(200);
        assertThat(user1Join.body()).contains("\"admitted\":true");
        String user1QueueToken = extractField(user1Join.body(), "admissionToken");

        HttpResponse<String> user2Join = send(
                "POST", "/api/queues/" + concertId + "/join", user2Token, null, null);
        assertThat(user2Join.statusCode()).isEqualTo(200);
        assertThat(user2Join.body())
                .contains("\"admitted\":false")
                .contains("\"position\":1");

        String reserveSeat1 = reservationPayload(sessionId, seatIds[0]);
        HttpResponse<String> bypass = send("POST", "/api/reservations", user1Token, reserveSeat1, null);
        assertThat(bypass.statusCode()).isEqualTo(403);

        HttpResponse<String> admittedReservation = send(
                "POST", "/api/reservations", user1Token, reserveSeat1, user1QueueToken);
        assertThat(admittedReservation.statusCode()).isEqualTo(201);

        Thread.sleep(1500);

        HttpResponse<String> user2Status = send(
                "GET", "/api/queues/" + concertId + "/me", user2Token, null, null);
        assertThat(user2Status.statusCode()).isEqualTo(200);
        assertThat(user2Status.body()).contains("\"admitted\":true");
        String user2QueueToken = extractField(user2Status.body(), "admissionToken");

        String reserveSeat2 = reservationPayload(sessionId, seatIds[1]);
        HttpResponse<String> expiredToken = send(
                "POST", "/api/reservations", user1Token, reserveSeat2, user1QueueToken);
        assertThat(expiredToken.statusCode()).isEqualTo(403);

        HttpResponse<String> user2Reservation = send(
                "POST", "/api/reservations", user2Token, reserveSeat2, user2QueueToken);
        assertThat(user2Reservation.statusCode()).isEqualTo(201);
    }

    @Test
    void expiredOwnerCannotReleaseLockAcquiredByAnotherWorker() throws Exception {
        String lockKey = "queue:lock:test:" + SEQUENCE.incrementAndGet();
        String firstOwner = "first-owner";
        String secondOwner = "second-owner";

        assertThat(admissionLock.acquire(lockKey, firstOwner, java.time.Duration.ofMillis(100))).isTrue();
        Thread.sleep(150);
        assertThat(admissionLock.acquire(lockKey, secondOwner, java.time.Duration.ofSeconds(1))).isTrue();

        admissionLock.release(lockKey, firstOwner);

        assertThat(redisTemplate.opsForValue().get(lockKey)).isEqualTo(secondOwner);
    }

    private String createToken(MemberRole role) {
        int seq = SEQUENCE.incrementAndGet();
        Member member = new Member("queue-user" + seq + "@test.com", "hash", "QueueUser" + seq, role);
        memberRepository.save(member);
        return jwtTokenProvider.createAccessToken(member);
    }

    private String createConcertDraft() throws Exception {
        Instant now = Instant.now();
        String payload = String.format("""
                {
                    "title": "Queue Concert %d",
                    "description": "Queue test",
                    "imageUrl": "https://example.com/queue.jpg",
                    "venue": "Queue Venue",
                    "holdSeconds": 300,
                    "maxTicketsPerUser": 2,
                    "sessions": [{
                        "startsAt": "%s",
                        "bookingOpensAt": "%s",
                        "bookingClosesAt": "%s",
                        "capacity": 2
                    }]
                }
                """,
                SEQUENCE.incrementAndGet(),
                now.plusSeconds(86400),
                now.minusSeconds(3600),
                now.plusSeconds(3600));
        HttpResponse<String> response = send("POST", "/api/admin/concerts", adminToken, payload, null);
        assertThat(response.statusCode()).isEqualTo(201);
        return extractField(response.body(), "id");
    }

    private String getFirstSessionId(String concertId) throws Exception {
        HttpResponse<String> response = send(
                "GET", "/api/admin/concerts/" + concertId, adminToken, null, null);
        Matcher matcher = Pattern.compile("\"sessions\":\\[\\{\"id\":\"([^\"]+)\"").matcher(response.body());
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Session ID not found");
    }

    private String[] createSeats(String concertId, String sessionId) throws Exception {
        String payload = """
                {
                    "section": "VIP",
                    "row": "A",
                    "startNumber": 1,
                    "count": 2,
                    "price": 100000
                }
                """;
        HttpResponse<String> response = send(
                "POST",
                "/api/admin/concerts/" + concertId + "/sessions/" + sessionId + "/seats",
                adminToken,
                payload,
                null);
        assertThat(response.statusCode()).isEqualTo(201);
        Matcher matcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(response.body());
        String[] ids = new String[2];
        for (int i = 0; i < ids.length; i++) {
            if (!matcher.find()) {
                throw new IllegalStateException("Seat ID not found");
            }
            ids[i] = matcher.group(1);
        }
        return ids;
    }

    private String reservationPayload(String sessionId, String seatId) {
        return String.format("{\"sessionId\":\"%s\",\"seatId\":\"%s\"}", sessionId, seatId);
    }

    private String extractField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Field " + field + " not found in: " + json);
    }

    private HttpResponse<String> send(
            String method,
            String path,
            String bearerToken,
            String body,
            String queueToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (queueToken != null) {
            builder.header("X-Queue-Token", queueToken);
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
