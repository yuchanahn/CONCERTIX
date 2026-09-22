package com.example.demo.seat;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.example.demo.TestInfrastructure;
import com.example.demo.auth.JwtTokenProvider;
import com.example.demo.concert.ConcertRepository;
import com.example.demo.user.Member;
import com.example.demo.user.MemberRepository;
import com.example.demo.user.MemberRole;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SeatApiIntegrationTests extends TestInfrastructure {

    private static final AtomicInteger EMAIL_SEQUENCE = new AtomicInteger();

    @LocalServerPort
    private int port;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final HttpClient http = HttpClient.newHttpClient();

    private String adminToken;
    private String userToken;

    @BeforeEach
    void cleanAndCreateMembers() {
        concertRepository.deleteAll();
        memberRepository.deleteAll();
        adminToken = token(MemberRole.ADMIN);
        userToken = token(MemberRole.USER);
    }

    @Test
    void adminCreatesAndUpdatesSeatsThenPublicCanReadPublishedSeatMap() throws Exception {
        HttpResponse<String> concert = send("POST", "/api/admin/concerts", adminToken, concertPayload(5));
        String concertId = stringField(concert.body(), "id");
        String sessionId = sessionId(concert.body());

        String seatPath = "/api/admin/concerts/" + concertId + "/sessions/" + sessionId + "/seats";
        HttpResponse<String> created = send("POST", seatPath, adminToken, seatsPayload(1, 3));
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(countOccurrences(created.body(), "\"status\":\"AVAILABLE\"")).isEqualTo(3);
        assertThat(created.body()).contains("\"number\":1", "\"number\":2", "\"number\":3");
        String seatId = stringField(created.body(), "id");

        String publicPath = "/api/concerts/" + concertId + "/sessions/" + sessionId + "/seats";
        assertThat(send("GET", publicPath, null, null).statusCode()).isEqualTo(404);

        HttpResponse<String> disabled = send(
                "PATCH",
                seatPath + "/" + seatId,
                adminToken,
                "{\"status\":\"DISABLED\"}");
        assertThat(disabled.statusCode()).isEqualTo(200);
        assertThat(stringField(disabled.body(), "status")).isEqualTo("DISABLED");

        HttpResponse<String> reenabled = send(
                "PATCH",
                seatPath + "/" + seatId,
                adminToken,
                "{\"status\":\"AVAILABLE\"}");
        assertThat(reenabled.statusCode()).isEqualTo(200);
        assertThat(stringField(reenabled.body(), "status")).isEqualTo("AVAILABLE");

        assertThat(send("PATCH", seatPath + "/" + seatId, adminToken, "{\"status\":\"DISABLED\"}").statusCode())
                .isEqualTo(200);

        HttpResponse<String> adminList = send("GET", seatPath, adminToken, null);
        assertThat(adminList.statusCode()).isEqualTo(200);
        assertThat(countOccurrences(adminList.body(), "\"id\":" )).isEqualTo(3);

        assertThat(send("POST", "/api/admin/concerts/" + concertId + "/publish", adminToken, null).statusCode())
                .isEqualTo(200);

        HttpResponse<String> publicList = send("GET", publicPath, null, null);
        assertThat(publicList.statusCode()).isEqualTo(200);
        assertThat(countOccurrences(publicList.body(), "\"id\":" )).isEqualTo(3);
        assertThat(publicList.body()).contains("\"status\":\"DISABLED\"");

        assertThat(send("POST", seatPath, adminToken, seatsPayload(4, 1)).statusCode()).isEqualTo(409);
        assertThat(send("PATCH", seatPath + "/" + seatId, adminToken, "{\"status\":\"AVAILABLE\"}").statusCode())
                .isEqualTo(409);
    }

    @Test
    void validatesAuthorizationCapacityDuplicatesAndAdminStatus() throws Exception {
        HttpResponse<String> concert = send("POST", "/api/admin/concerts", adminToken, concertPayload(3));
        String concertId = stringField(concert.body(), "id");
        String sessionId = sessionId(concert.body());
        String seatPath = "/api/admin/concerts/" + concertId + "/sessions/" + sessionId + "/seats";

        assertThat(send("POST", seatPath, null, seatsPayload(1, 1)).statusCode()).isEqualTo(401);
        assertThat(send("POST", seatPath, userToken, seatsPayload(1, 1)).statusCode()).isEqualTo(403);
        assertThat(send("POST", seatPath, adminToken, seatsPayload(1, 0)).statusCode()).isEqualTo(400);
        assertThat(send("POST", seatPath, adminToken, seatsPayload(1, 4)).statusCode()).isEqualTo(409);

        HttpResponse<String> created = send("POST", seatPath, adminToken, seatsPayload(1, 2));
        assertThat(created.statusCode()).isEqualTo(201);
        String seatId = stringField(created.body(), "id");
        assertThat(send("POST", seatPath, adminToken, seatsPayload(1, 1)).statusCode()).isEqualTo(409);
        assertThat(send("PATCH", seatPath + "/" + seatId, adminToken, "{\"status\":\"HELD\"}").statusCode())
                .isEqualTo(400);
        assertThat(send("PATCH", seatPath + "/00000000-0000-0000-0000-000000000001", adminToken,
                "{\"status\":\"DISABLED\"}").statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/admin/concerts/" + concertId + "/sessions/not-a-uuid/seats", adminToken, null)
                .statusCode()).isEqualTo(400);
    }

    @Test
    void putDraftWithSeatsReturnsConflictAndKeepsConcertSessionAndSeat() throws Exception {
        HttpResponse<String> concert = send("POST", "/api/admin/concerts", adminToken, concertPayload(5));
        assertThat(concert.statusCode()).isEqualTo(201);
        String concertId = stringField(concert.body(), "id");
        String originalTitle = stringField(concert.body(), "title");
        String originalSessionId = sessionId(concert.body());

        String seatPath = "/api/admin/concerts/" + concertId + "/sessions/" + originalSessionId + "/seats";
        HttpResponse<String> createdSeats = send("POST", seatPath, adminToken, seatsPayload(1, 1));
        assertThat(createdSeats.statusCode()).isEqualTo(201);
        String originalSeatId = stringField(createdSeats.body(), "id");
        long originalPrice = longField(createdSeats.body(), "price");

        String changedPayload = concertPayload(5).replace("Seat Test Live", "Changed Seat Test Live");
        HttpResponse<String> failedUpdate = send(
                "PUT",
                "/api/admin/concerts/" + concertId,
                adminToken,
                changedPayload);
        assertThat(failedUpdate.statusCode()).isEqualTo(409);

        HttpResponse<String> detail = send("GET", "/api/admin/concerts/" + concertId, adminToken, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(stringField(detail.body(), "title")).isEqualTo(originalTitle);
        assertThat(sessionId(detail.body())).isEqualTo(originalSessionId);

        HttpResponse<String> seats = send("GET", seatPath, adminToken, null);
        assertThat(seats.statusCode()).isEqualTo(200);
        assertThat(stringField(seats.body(), "id")).isEqualTo(originalSeatId);
        assertThat(longField(seats.body(), "price")).isEqualTo(originalPrice);
    }

    private String token(MemberRole role) {
        int sequence = EMAIL_SEQUENCE.incrementAndGet();
        Member member = memberRepository.save(new Member(
                role.name().toLowerCase() + "-seat-" + sequence + "@example.com",
                "hash",
                role.name().toLowerCase() + sequence,
                role));
        return jwtTokenProvider.createAccessToken(member);
    }

    private HttpResponse<String> send(String method, String path, String bearerToken, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, publisher);
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String concertPayload(int capacity) {
        return """
                {
                  "title": "Seat Test Live",
                  "description": "seat test",
                  "imageUrl": "https://example.com/seat.jpg",
                  "venue": "Seoul Hall",
                  "holdSeconds": 300,
                  "maxTicketsPerUser": 2,
                  "sessions": [
                    {
                      "startsAt": "2026-12-20T19:00:00+09:00",
                      "bookingOpensAt": "2026-11-01T10:00:00+09:00",
                      "bookingClosesAt": "2026-12-20T18:00:00+09:00",
                      "capacity": %d
                    }
                  ]
                }
                """.formatted(capacity);
    }

    private String seatsPayload(int startNumber, int count) {
        return """
                {
                  "section": "A",
                  "row": "1",
                  "startNumber": %d,
                  "count": %d,
                  "price": 120000
                }
                """.formatted(startNumber, count);
    }

    private String stringField(String json, String field) {
        var matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).as("field %s in %s", field, json).isTrue();
        return matcher.group(1);
    }

    private long longField(String json, String field) {
        var matcher = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        assertThat(matcher.find()).as("field %s in %s", field, json).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private String sessionId(String json) {
        var matcher = Pattern.compile("\"sessions\":\\[(.*?)]").matcher(json);
        assertThat(matcher.find()).as("sessions in %s", json).isTrue();
        return stringField(matcher.group(1), "id");
    }

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
