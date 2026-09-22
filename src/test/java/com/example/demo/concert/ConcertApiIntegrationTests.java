package com.example.demo.concert;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.example.demo.TestInfrastructure;
import com.example.demo.auth.JwtTokenProvider;
import com.example.demo.user.Member;
import com.example.demo.user.MemberRepository;
import com.example.demo.user.MemberRole;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcertApiIntegrationTests extends TestInfrastructure {

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
    void adminLifecycleCreatesReplacesSessionsPublishesAndBlocksPublishedChanges() throws Exception {
        HttpResponse<String> created = send("POST", "/api/admin/concerts", adminToken, createPayload());
        assertThat(created.statusCode()).isEqualTo(201);
        String id = stringField(created.body(), "id");
        assertThat(stringField(created.body(), "status")).isEqualTo("PREPARING");
        assertThat(countOccurrences(created.body(), "\"startsAt\"")).isEqualTo(2);
        assertThat(created.body()).contains("\"startsAt\":\"2026-12-20T10:00:00Z\"");
        String originalSessionId = sessionId(created.body(), 0);

        assertThat(send("GET", "/api/concerts/" + id, null, null).statusCode()).isEqualTo(404);

        HttpResponse<String> adminDetail = send("GET", "/api/admin/concerts/" + id, adminToken, null);
        assertThat(adminDetail.statusCode()).isEqualTo(200);
        assertThat(countOccurrences(adminDetail.body(), "\"startsAt\"")).isEqualTo(2);

        HttpResponse<String> updated = send("PUT", "/api/admin/concerts/" + id, adminToken, updatePayload());
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(stringField(updated.body(), "title")).isEqualTo("Winter Live");
        assertThat(intField(updated.body(), "maxTicketsPerUser")).isEqualTo(3);
        assertThat(countOccurrences(updated.body(), "\"startsAt\"")).isEqualTo(1);
        assertThat(sessionId(updated.body(), 0)).isNotEqualTo(originalSessionId);

        HttpResponse<String> published = send("POST", "/api/admin/concerts/" + id + "/publish", adminToken, null);
        assertThat(published.statusCode()).isEqualTo(200);
        assertThat(stringField(published.body(), "status")).isEqualTo("PUBLISHED");

        HttpResponse<String> publicList = send("GET", "/api/concerts?page=0&size=20", null, null);
        assertThat(publicList.statusCode()).isEqualTo(200);
        assertThat(longField(publicList.body(), "totalElements")).isEqualTo(1);
        assertThat(publicList.body()).contains("\"id\":\"" + id + "\"");

        HttpResponse<String> publicDetail = send("GET", "/api/concerts/" + id, null, null);
        assertThat(publicDetail.statusCode()).isEqualTo(200);
        assertThat(countOccurrences(publicDetail.body(), "\"startsAt\"")).isEqualTo(1);

        assertThat(send("POST", "/api/admin/concerts/" + id + "/publish", adminToken, null).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/admin/concerts/" + id, adminToken, updatePayload()).statusCode()).isEqualTo(409);
        assertThat(send("DELETE", "/api/admin/concerts/" + id, adminToken, null).statusCode()).isEqualTo(409);
    }

    @Test
    void draftCanBeListedByAdminAndDeleted() throws Exception {
        String id = stringField(send("POST", "/api/admin/concerts", adminToken, createPayload()).body(), "id");

        HttpResponse<String> adminList = send("GET", "/api/admin/concerts?page=0&size=1", adminToken, null);
        assertThat(adminList.statusCode()).isEqualTo(200);
        assertThat(longField(adminList.body(), "totalElements")).isEqualTo(1);
        assertThat(adminList.body()).contains("\"status\":\"PREPARING\"");

        assertThat(send("DELETE", "/api/admin/concerts/" + id, adminToken, null).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/admin/concerts/" + id, adminToken, null).statusCode()).isEqualTo(404);
    }

    @Test
    void adminEndpointsRequireAdminAndPublicGetAllowsAnonymousOnlyForPublicRoutes() throws Exception {
        assertThat(send("POST", "/api/admin/concerts", null, createPayload()).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/admin/concerts", userToken, createPayload()).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/concerts?page=0&size=20", null, null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/concerts", null, createPayload()).statusCode()).isEqualTo(401);
    }

    @Test
    void validatesRequestBodyPageAndUuidFailures() throws Exception {
        assertThat(send("POST", "/api/admin/concerts", adminToken, invalidPeriodPayload()).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/admin/concerts", adminToken, maxTicketsAboveCapacityPayload()).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/admin/concerts", adminToken, duplicateStartsAtPayload()).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/admin/concerts", adminToken, badUrlPayload()).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/admin/concerts", adminToken, missingTitlePayload()).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/admin/concerts", adminToken, nullSessionPayload()).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/concerts?page=-1&size=20", null, null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/concerts?page=0&size=101", null, null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/concerts/not-a-uuid", null, null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/admin/concerts/" + UUID.randomUUID(), adminToken, null).statusCode()).isEqualTo(404);
    }

    @Test
    void putWithNullSessionReturnsBadRequestAndKeepsExistingConcert() throws Exception {
        HttpResponse<String> created = send("POST", "/api/admin/concerts", adminToken, createPayload());
        assertThat(created.statusCode()).isEqualTo(201);
        String id = stringField(created.body(), "id");

        HttpResponse<String> failedUpdate = send("PUT", "/api/admin/concerts/" + id, adminToken, nullSessionPayload());
        assertThat(failedUpdate.statusCode()).isEqualTo(400);

        HttpResponse<String> detail = send("GET", "/api/admin/concerts/" + id, adminToken, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(stringField(detail.body(), "title")).isEqualTo("Autumn Live");
        assertThat(intField(detail.body(), "maxTicketsPerUser")).isEqualTo(4);
        assertThat(countOccurrences(detail.body(), "\"startsAt\"")).isEqualTo(2);
        assertThat(detail.body()).contains("\"startsAt\":\"2026-12-20T10:00:00Z\"");
        assertThat(detail.body()).contains("\"startsAt\":\"2026-12-21T10:00:00Z\"");
    }

    private String token(MemberRole role) {
        int sequence = EMAIL_SEQUENCE.incrementAndGet();
        Member member = memberRepository.save(new Member(
                role.name().toLowerCase() + sequence + "@example.com",
                "hash",
                role.name().toLowerCase() + sequence,
                role));
        return jwtTokenProvider.createAccessToken(member);
    }

    private HttpResponse<String> send(String method, String path, String bearerToken, String body) throws Exception {
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri(path))
                .method(method, publisher);
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI baseUri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String createPayload() {
        return """
                {
                  "title": "Autumn Live",
                  "description": "first show",
                  "imageUrl": "https://example.com/poster.jpg",
                  "venue": "Seoul Hall",
                  "holdSeconds": 300,
                  "maxTicketsPerUser": 4,
                  "sessions": [
                    {
                      "startsAt": "2026-12-21T19:00:00+09:00",
                      "bookingOpensAt": "2026-11-01T10:00:00+09:00",
                      "bookingClosesAt": "2026-12-21T18:00:00+09:00",
                      "capacity": 100
                    },
                    {
                      "startsAt": "2026-12-20T19:00:00+09:00",
                      "bookingOpensAt": "2026-11-01T10:00:00+09:00",
                      "bookingClosesAt": "2026-12-20T18:00:00+09:00",
                      "capacity": 100
                    }
                  ]
                }
                """;
    }

    private String updatePayload() {
        return """
                {
                  "title": "Winter Live",
                  "description": "updated show",
                  "imageUrl": "http://example.com/winter.jpg",
                  "venue": "Busan Hall",
                  "holdSeconds": 120,
                  "maxTicketsPerUser": 3,
                  "sessions": [
                    {
                      "startsAt": "2027-01-10T19:00:00+09:00",
                      "bookingOpensAt": "2026-12-01T10:00:00+09:00",
                      "bookingClosesAt": "2027-01-10T18:00:00+09:00",
                      "capacity": 50
                    }
                  ]
                }
                """;
    }

    private String invalidPeriodPayload() {
        return createPayload().replace(
                "\"bookingOpensAt\": \"2026-11-01T10:00:00+09:00\"",
                "\"bookingOpensAt\": \"2026-12-21T18:00:00+09:00\"");
    }

    private String maxTicketsAboveCapacityPayload() {
        return createPayload()
                .replace("\"maxTicketsPerUser\": 4", "\"maxTicketsPerUser\": 10")
                .replace("\"capacity\": 100", "\"capacity\": 5");
    }

    private String duplicateStartsAtPayload() {
        return createPayload().replace(
                "\"startsAt\": \"2026-12-20T19:00:00+09:00\"",
                "\"startsAt\": \"2026-12-21T19:00:00+09:00\"");
    }

    private String badUrlPayload() {
        return createPayload().replace("https://example.com/poster.jpg", "ftp://example.com/poster.jpg");
    }

    private String missingTitlePayload() {
        return createPayload().replace("\"title\": \"Autumn Live\"", "\"title\": \"\"");
    }

    private String nullSessionPayload() {
        return """
                {
                  "title": "Null Session Live",
                  "description": "bad show",
                  "imageUrl": "https://example.com/null-session.jpg",
                  "venue": "Seoul Hall",
                  "holdSeconds": 300,
                  "maxTicketsPerUser": 4,
                  "sessions": [null]
                }
                """;
    }

    private String stringField(String json, String field) {
        var matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).as("field %s in %s", field, json).isTrue();
        return matcher.group(1);
    }

    private int intField(String json, String field) {
        return Math.toIntExact(longField(json, field));
    }

    private long longField(String json, String field) {
        var matcher = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        assertThat(matcher.find()).as("field %s in %s", field, json).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private String sessionId(String json, int index) {
        var matcher = Pattern.compile("\"sessions\":\\[(.*)]").matcher(json);
        assertThat(matcher.find()).as("sessions in %s", json).isTrue();
        String sessions = matcher.group(1);
        var idMatcher = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(sessions);
        for (int i = 0; i <= index; i++) {
            assertThat(idMatcher.find()).as("session id %s in %s", index, json).isTrue();
        }
        return idMatcher.group(1);
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
