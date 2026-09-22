package com.example.demo.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.example.demo.concert.ConcertRepository;
import com.example.demo.concert.ConcertStatus;
import com.example.demo.queue.QueueDtos.QueueStatusResponse;
import com.example.demo.queue.QueueExceptions.QueueAdmissionRequiredException;
import com.example.demo.queue.QueueExceptions.QueueConcertNotFoundException;

@Service
public class QueueService {

    private static final String PREFIX = "queue:";
    private static final DefaultRedisScript<Long> ADMIT_AVAILABLE = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local maxAdmitted = tonumber(ARGV[2])
            local ttlMillis = tonumber(ARGV[3])
            local concertId = ARGV[4]
            local memberTokenPrefix = ARGV[5]
            local tokenPrefix = ARGV[6]

            redis.call('ZREMRANGEBYSCORE', KEYS[2], 0, now)
            local slots = math.max(0, maxAdmitted - redis.call('ZCARD', KEYS[2]))
            for i = 1, slots do
                local nextMember = redis.call('ZPOPMIN', KEYS[1], 1)
                if #nextMember == 0 then
                    break
                end

                local memberId = nextMember[1]
                local token = ARGV[6 + i]
                redis.call('SET', memberTokenPrefix .. memberId, token, 'PX', ttlMillis)
                redis.call('SET', tokenPrefix .. token, concertId .. ':' .. memberId, 'PX', ttlMillis)
                redis.call('ZADD', KEYS[2], now + ttlMillis, memberId)
            end
            return redis.call('ZCARD', KEYS[2])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ConcertRepository concertRepository;
    private final int maxAdmitted;
    private final Duration admissionTtl;

    public QueueService(
            StringRedisTemplate redisTemplate,
            ConcertRepository concertRepository,
            @Value("${app.queue.max-admitted:20}") int maxAdmitted,
            @Value("${app.queue.admission-ttl:2m}") Duration admissionTtl) {
        this.redisTemplate = redisTemplate;
        this.concertRepository = concertRepository;
        this.maxAdmitted = maxAdmitted;
        this.admissionTtl = admissionTtl;
    }

    public QueueStatusResponse activate(UUID concertId) {
        requirePublishedConcert(concertId);
        redisTemplate.opsForValue().set(activeKey(concertId), "1");
        return new QueueStatusResponse(true, false, null, null, null);
    }

    public QueueStatusResponse deactivate(UUID concertId) {
        redisTemplate.delete(Set.of(
                activeKey(concertId),
                waitingKey(concertId),
                admittedKey(concertId),
                sequenceKey(concertId)));
        deleteMemberTokens(concertId);
        return new QueueStatusResponse(false, true, 0L, null, null);
    }

    public QueueStatusResponse join(UUID concertId, UUID memberId) {
        requirePublishedConcert(concertId);
        if (!isActive(concertId)) {
            return openAccessResponse();
        }

        String member = memberId.toString();
        if (!hasAdmission(concertId, memberId)
                && redisTemplate.opsForZSet().rank(waitingKey(concertId), member) == null) {
            Long sequence = redisTemplate.opsForValue().increment(sequenceKey(concertId));
            redisTemplate.opsForZSet().add(waitingKey(concertId), member, sequence == null ? 0 : sequence.doubleValue());
        }

        admitAvailable(concertId);
        return statusInternal(concertId, memberId);
    }

    public QueueStatusResponse status(UUID concertId, UUID memberId) {
        requirePublishedConcert(concertId);
        if (!isActive(concertId)) {
            return openAccessResponse();
        }
        admitAvailable(concertId);
        return statusInternal(concertId, memberId);
    }

    public void validateAdmission(UUID concertId, UUID memberId, String token) {
        if (!isActive(concertId)) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new QueueAdmissionRequiredException("Queue admission token is required");
        }
        String owner = redisTemplate.opsForValue().get(tokenKey(token));
        String expected = concertId + ":" + memberId;
        if (!expected.equals(owner)) {
            throw new QueueAdmissionRequiredException("Queue admission token is invalid or expired");
        }
    }

    public boolean isActive(UUID concertId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(activeKey(concertId)));
    }

    private void admitAvailable(UUID concertId) {
        Object[] arguments = new Object[6 + maxAdmitted];
        arguments[0] = Long.toString(System.currentTimeMillis());
        arguments[1] = Integer.toString(maxAdmitted);
        arguments[2] = Long.toString(admissionTtl.toMillis());
        arguments[3] = concertId.toString();
        arguments[4] = PREFIX + "member-token:" + concertId + ":";
        arguments[5] = PREFIX + "token:";
        for (int i = 0; i < maxAdmitted; i++) {
            arguments[6 + i] = UUID.randomUUID().toString();
        }
        redisTemplate.execute(
                ADMIT_AVAILABLE,
                List.of(waitingKey(concertId), admittedKey(concertId)),
                arguments);
    }

    private QueueStatusResponse statusInternal(UUID concertId, UUID memberId) {
        String token = redisTemplate.opsForValue().get(memberTokenKey(concertId, memberId));
        if (token != null) {
            Long ttlSeconds = redisTemplate.getExpire(memberTokenKey(concertId, memberId));
            Instant expiresAt = ttlSeconds != null && ttlSeconds > 0
                    ? Instant.now().plusSeconds(ttlSeconds)
                    : null;
            return new QueueStatusResponse(true, true, 0L, token, expiresAt);
        }

        Long rank = redisTemplate.opsForZSet().rank(waitingKey(concertId), memberId.toString());
        Long position = rank == null ? null : rank + 1;
        return new QueueStatusResponse(true, false, position, null, null);
    }

    private boolean hasAdmission(UUID concertId, UUID memberId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(memberTokenKey(concertId, memberId)));
    }

    private QueueStatusResponse openAccessResponse() {
        return new QueueStatusResponse(false, true, 0L, null, null);
    }

    private void requirePublishedConcert(UUID concertId) {
        boolean published = concertRepository.findById(concertId)
                .map(concert -> concert.getStatus() == ConcertStatus.PUBLISHED)
                .orElse(false);
        if (!published) {
            throw new QueueConcertNotFoundException();
        }
    }

    private void deleteMemberTokens(UUID concertId) {
        Set<String> memberTokenKeys = redisTemplate.keys(PREFIX + "member-token:" + concertId + ":*");
        if (memberTokenKeys != null && !memberTokenKeys.isEmpty()) {
            for (String key : memberTokenKeys) {
                String token = redisTemplate.opsForValue().get(key);
                if (token != null) {
                    redisTemplate.delete(tokenKey(token));
                }
            }
            redisTemplate.delete(memberTokenKeys);
        }
    }

    private String activeKey(UUID concertId) {
        return PREFIX + "active:" + concertId;
    }

    private String waitingKey(UUID concertId) {
        return PREFIX + "waiting:" + concertId;
    }

    private String admittedKey(UUID concertId) {
        return PREFIX + "admitted:" + concertId;
    }

    private String sequenceKey(UUID concertId) {
        return PREFIX + "sequence:" + concertId;
    }

    private String memberTokenKey(UUID concertId, UUID memberId) {
        return PREFIX + "member-token:" + concertId + ":" + memberId;
    }

    private String tokenKey(String token) {
        return PREFIX + "token:" + token;
    }
}
