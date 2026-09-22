package com.example.demo.auth;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.auth.AuthDtos.LoginRequest;
import com.example.demo.auth.AuthDtos.RegisterRequest;
import com.example.demo.auth.AuthDtos.TokenResponse;
import com.example.demo.user.Member;
import com.example.demo.user.MemberRepository;
import com.example.demo.user.MemberRole;

@Service
public class AuthService {

    private static final String REFRESH_PREFIX = "auth:refresh:";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTokenTtl;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            StringRedisTemplate redisTemplate,
            @Value("${app.auth.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisTemplate = redisTemplate;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (memberRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        Member member = new Member(
                email,
                passwordEncoder.encode(request.password()),
                request.nickname().trim(),
                MemberRole.USER);
        memberRepository.save(member);
        return issueTokens(member);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> unauthorized("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw unauthorized("Invalid email or password");
        }

        return issueTokens(member);
    }

    public TokenResponse refresh(String refreshToken) {
        String key = refreshKey(refreshToken);
        String memberId = redisTemplate.opsForValue().getAndDelete(key);
        if (memberId == null) {
            throw unauthorized("Invalid or expired refresh token");
        }

        Member member = memberRepository.findById(UUID.fromString(memberId))
                .orElseThrow(() -> unauthorized("Invalid refresh token owner"));

        return issueTokens(member);
    }

    public void logout(String refreshToken) {
        redisTemplate.delete(refreshKey(refreshToken));
    }

    private TokenResponse issueTokens(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                refreshKey(refreshToken),
                member.getId().toString(),
                refreshTokenTtl);

        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.accessTokenTtlSeconds(),
                new AuthDtos.MemberResponse(
                        member.getId(),
                        member.getEmail(),
                        member.getNickname(),
                        member.getRole().name()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String refreshKey(String token) {
        return REFRESH_PREFIX + token;
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
