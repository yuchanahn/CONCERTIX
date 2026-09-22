package com.example.demo.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.demo.user.Member;
import com.example.demo.user.MemberRole;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtTokenProvider(
            @Value("${app.auth.jwt-secret}") String secret,
            @Value("${app.auth.access-token-ttl}") Duration accessTokenTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = accessTokenTtl;
    }

    public String createAccessToken(Member member) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(member.getId().toString())
                .claim("email", member.getEmail())
                .claim("role", member.getRole().name())
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    public AccessPrincipal parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!"access".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Access token required");
        }

        return new AccessPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                MemberRole.valueOf(claims.get("role", String.class)));
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    public record AccessPrincipal(UUID memberId, String email, MemberRole role) {
    }
}
