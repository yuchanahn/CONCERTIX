package com.example.demo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.auth.AuthDtos.LoginRequest;
import com.example.demo.auth.AuthDtos.RegisterRequest;
import com.example.demo.auth.AuthDtos.TokenResponse;
import com.example.demo.user.MemberRepository;
import com.example.demo.TestInfrastructure;

class AuthServiceIntegrationTests extends TestInfrastructure {

    @Autowired
    private AuthService authService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clean() {
        memberRepository.deleteAll();
        var keys = redisTemplate.keys("auth:refresh:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void registerLoginRefreshAndLogout() {
        TokenResponse registered = authService.register(
                new RegisterRequest("User@Example.com", "password123", "tester"));

        assertThat(registered.member().email()).isEqualTo("user@example.com");
        assertThat(registered.accessToken()).isNotBlank();
        assertThat(registered.refreshToken()).isNotBlank();

        TokenResponse loggedIn = authService.login(
                new LoginRequest("user@example.com", "password123"));
        assertThat(loggedIn.member().id()).isEqualTo(registered.member().id());

        String oldRefresh = loggedIn.refreshToken();
        TokenResponse refreshed = authService.refresh(oldRefresh);
        assertThat(refreshed.refreshToken()).isNotEqualTo(oldRefresh);

        assertThatThrownBy(() -> authService.refresh(oldRefresh))
                .isInstanceOf(ResponseStatusException.class);

        authService.logout(refreshed.refreshToken());
        assertThatThrownBy(() -> authService.refresh(refreshed.refreshToken()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void refreshTokenCanBeConsumedOnlyOnceUnderConcurrency() throws Exception {
        TokenResponse registered = authService.register(
                new RegisterRequest("race@example.com", "password123", "race"));
        String refreshToken = registered.refreshToken();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(
                    () -> attemptRefresh(refreshToken, start), executor);
            CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(
                    () -> attemptRefresh(refreshToken, start), executor);

            start.countDown();

            long successes = java.util.stream.Stream.of(first.get(), second.get())
                    .filter(Boolean::booleanValue)
                    .count();
            assertThat(successes).isEqualTo(1);
        }
    }

    private boolean attemptRefresh(String refreshToken, CountDownLatch start) {
        try {
            start.await();
            authService.refresh(refreshToken);
            return true;
        } catch (ResponseStatusException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
