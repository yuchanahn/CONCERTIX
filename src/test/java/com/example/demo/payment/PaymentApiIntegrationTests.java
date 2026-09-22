package com.example.demo.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.example.demo.TestInfrastructure;
import com.example.demo.auth.JwtTokenProvider;
import com.example.demo.concert.ConcertRepository;
import com.example.demo.reservation.ReservationRepository;
import com.example.demo.user.Member;
import com.example.demo.user.MemberRepository;
import com.example.demo.user.MemberRole;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentApiIntegrationTests extends TestInfrastructure {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @LocalServerPort
    private int port;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ConcertRepository concertRepository;

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

    @AfterEach
    void teardown() {
        cleanData();
    }

    private void cleanData() {
        paymentRepository.deleteAll();
        reservationRepository.deleteAll();
        concertRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void successfulPaymentConfirmsReservationAndReservationCancelCancelsPayment() throws Exception {
        Booking booking = createBooking(300);

        HttpResponse<String> created = createPayment(booking.reservationId(), user1Token);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"status\":\"PENDING\"");
        String paymentId = extractField(created.body(), "id");

        HttpResponse<String> duplicateCreate = createPayment(booking.reservationId(), user1Token);
        assertThat(duplicateCreate.statusCode()).isEqualTo(201);
        assertThat(extractField(duplicateCreate.body(), "id")).isEqualTo(paymentId);

        HttpResponse<String> forbidden = send("GET", "/api/payments/" + paymentId, user2Token, null);
        assertThat(forbidden.statusCode()).isEqualTo(403);

        HttpResponse<String> success = completePayment(paymentId, "SUCCESS", user1Token);
        assertThat(success.statusCode()).isEqualTo(200);
        assertThat(success.body())
                .contains("\"status\":\"SUCCESS\"")
                .contains("\"reservationStatus\":\"CONFIRMED\"")
                .contains("\"seatStatus\":\"RESERVED\"");

        HttpResponse<String> duplicateSuccess = completePayment(paymentId, "SUCCESS", user1Token);
        assertThat(duplicateSuccess.statusCode()).isEqualTo(200);
        assertThat(duplicateSuccess.body()).contains("\"status\":\"SUCCESS\"");

        HttpResponse<String> conflictingResult = completePayment(paymentId, "FAILED", user1Token);
        assertThat(conflictingResult.statusCode()).isEqualTo(409);

        HttpResponse<String> cancelReservation = send(
                "POST",
                "/api/reservations/" + booking.reservationId() + "/cancel",
                user1Token,
                null);
        assertThat(cancelReservation.statusCode()).isEqualTo(200);
        assertThat(cancelReservation.body()).contains("\"status\":\"CANCELED\"");

        HttpResponse<String> paymentAfterCancel = send("GET", "/api/payments/" + paymentId, user1Token, null);
        assertThat(paymentAfterCancel.statusCode()).isEqualTo(200);
        assertThat(paymentAfterCancel.body())
                .contains("\"status\":\"CANCELED\"")
                .contains("\"seatStatus\":\"AVAILABLE\"");
    }

    @Test
    void failedPaymentReleasesSeatAndSameFailureResultIsIdempotent() throws Exception {
        Booking booking = createBooking(300);
        String paymentId = extractField(createPayment(booking.reservationId(), user1Token).body(), "id");

        HttpResponse<String> failed = completePayment(paymentId, "FAILED", user1Token);
        assertThat(failed.statusCode()).isEqualTo(200);
        assertThat(failed.body())
                .contains("\"status\":\"FAILED\"")
                .contains("\"reservationStatus\":\"CANCELED\"")
                .contains("\"seatStatus\":\"AVAILABLE\"");

        HttpResponse<String> duplicateFailure = completePayment(paymentId, "FAILED", user1Token);
        assertThat(duplicateFailure.statusCode()).isEqualTo(200);
        assertThat(duplicateFailure.body()).contains("\"status\":\"FAILED\"");

        HttpResponse<String> cancelFailedPayment = send(
                "POST",
                "/api/payments/" + paymentId + "/cancel",
                user1Token,
                null);
        assertThat(cancelFailedPayment.statusCode()).isEqualTo(409);

        String reservePayload = String.format(
                "{\"sessionId\":\"%s\",\"seatId\":\"%s\"}",
                booking.sessionId(),
                booking.seatId());
        HttpResponse<String> reclaimed = send("POST", "/api/reservations", user2Token, reservePayload);
        assertThat(reclaimed.statusCode()).isEqualTo(201);
    }

    @Test
    void expiredHoldCannotBeCompletedAsSuccessfulPayment() throws Exception {
        Booking booking = createBooking(1);
        String paymentId = extractField(createPayment(booking.reservationId(), user1Token).body(), "id");

        Thread.sleep(1500);

        HttpResponse<String> expiredSuccess = completePayment(paymentId, "SUCCESS", user1Token);
        assertThat(expiredSuccess.statusCode()).isEqualTo(409);

        HttpResponse<String> refreshedSeats = send(
                "GET",
                "/api/concerts/" + booking.concertId() + "/sessions/" + booking.sessionId() + "/seats",
                null,
                null);
        assertThat(refreshedSeats.statusCode()).isEqualTo(200);
        assertThat(refreshedSeats.body()).contains("\"status\":\"AVAILABLE\"");

        HttpResponse<String> payment = send("GET", "/api/payments/" + paymentId, user1Token, null);
        assertThat(payment.statusCode()).isEqualTo(200);
        assertThat(payment.body())
                .contains("\"status\":\"PENDING\"")
                .contains("\"reservationStatus\":\"EXPIRED\"")
                .contains("\"seatStatus\":\"AVAILABLE\"");
    }

    private Booking createBooking(int holdSeconds) throws Exception {
        String concertId = createConcertDraft(holdSeconds);
        String sessionId = getFirstSessionId(concertId);
        String seatId = createSingleSeat(concertId, sessionId);
        send("POST", "/api/admin/concerts/" + concertId + "/publish", adminToken, null);

        String reservePayload = String.format(
                "{\"sessionId\":\"%s\",\"seatId\":\"%s\"}",
                sessionId,
                seatId);
        HttpResponse<String> reservation = send("POST", "/api/reservations", user1Token, reservePayload);
        assertThat(reservation.statusCode()).isEqualTo(201);
        return new Booking(concertId, sessionId, seatId, extractField(reservation.body(), "id"));
    }

    private HttpResponse<String> createPayment(String reservationId, String token) throws Exception {
        return send("POST", "/api/payments", token, "{\"reservationId\":\"" + reservationId + "\"}");
    }

    private HttpResponse<String> completePayment(String paymentId, String result, String token) throws Exception {
        return send(
                "POST",
                "/api/payments/" + paymentId + "/complete",
                token,
                "{\"result\":\"" + result + "\"}");
    }

    private String createToken(MemberRole role) {
        int seq = SEQUENCE.incrementAndGet();
        Member member = new Member("payment-user" + seq + "@test.com", "hash", "PaymentUser" + seq, role);
        memberRepository.save(member);
        return jwtTokenProvider.createAccessToken(member);
    }

    private String createConcertDraft(int holdSeconds) throws Exception {
        Instant now = Instant.now();
        String payload = String.format("""
                {
                    "title": "Payment Concert %d",
                    "description": "Payment test",
                    "imageUrl": "https://example.com/payment.jpg",
                    "venue": "Venue",
                    "holdSeconds": %d,
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
                holdSeconds,
                now.plusSeconds(86400),
                now.minusSeconds(3600),
                now.plusSeconds(3600));
        HttpResponse<String> response = send("POST", "/api/admin/concerts", adminToken, payload);
        assertThat(response.statusCode()).isEqualTo(201);
        return extractField(response.body(), "id");
    }

    private String getFirstSessionId(String concertId) throws Exception {
        HttpResponse<String> response = send("GET", "/api/admin/concerts/" + concertId, adminToken, null);
        Matcher matcher = Pattern.compile("\"sessions\":\\[\\{\"id\":\"([^\"]+)\"").matcher(response.body());
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
        HttpResponse<String> response = send(
                "POST",
                "/api/admin/concerts/" + concertId + "/sessions/" + sessionId + "/seats",
                adminToken,
                payload);
        assertThat(response.statusCode()).isEqualTo(201);
        return extractField(response.body(), "id");
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

    private record Booking(String concertId, String sessionId, String seatId, String reservationId) {
    }
}
