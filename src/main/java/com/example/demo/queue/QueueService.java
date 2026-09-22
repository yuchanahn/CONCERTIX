package com.example.demo.queue;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import com.example.demo.concert.ConcertRepository;
import com.example.demo.concert.ConcertStatus;
import com.example.demo.queue.QueueDtos.QueueStatusResponse;
import com.example.demo.queue.QueueExceptions.QueueAdmissionRequiredException;
import com.example.demo.queue.QueueExceptions.QueueConcertNotFoundException;

@Service
public class QueueService {

    private static final String PREFIX = "queue:";

    private final StringRedisTemplate redisTemplate;
    private final QueueAdmissionLock admissionLock;
    private final ConcertRepository concertRepository;
    private final int maxAdmitted;
    private final Duration admissionTtl;

    public QueueService(
            StringRedisTemplate redisTemplate,
            QueueAdmissionLock admissionLock,
            ConcertRepository concertRepository,
            @Value("${app.queue.max-admitted:20}") int maxAdmitted,
            @Value("${app.queue.admission-ttl:2m}") Duration admissionTtl) {
        this.redisTemplate = redisTemplate;
        this.admissionLock = admissionLock;
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
        String lockKey = lockKey(concertId);
        String ownerId = UUID.randomUUID().toString();
        if (!admissionLock.acquire(lockKey, ownerId, Duration.ofSeconds(2))) {
            return;
        }

        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForZSet().removeRangeByScore(admittedKey(concertId), 0, now);
            Long admittedCount = redisTemplate.opsForZSet().zCard(admittedKey(concertId));
            long slots = Math.max(0, maxAdmitted - (admittedCount == null ? 0 : admittedCount));
            if (slots == 0) {
                return;
            }

            Set<TypedTuple<String>> nextMembers = redisTemplate.opsForZSet().popMin(waitingKey(concertId), slots);
            if (nextMembers == null || nextMembers.isEmpty()) {
                return;
            }

            Instant expiresAt = Instant.now().plus(admissionTtl);
            for (TypedTuple<String> tuple : nextMembers) {
                String member = tuple.getValue();
                if (member == null) {
                    continue;
                }
                UUID memberId = UUID.fromString(member);
                String token = UUID.randomUUID().toString();
                redisTemplate.opsForValue().set(memberTokenKey(concertId, memberId), token, admissionTtl);
                redisTemplate.opsForValue().set(tokenKey(token), concertId + ":" + memberId, admissionTtl);
                redisTemplate.opsForZSet().add(admittedKey(concertId), member, expiresAt.toEpochMilli());
            }
        } finally {
            admissionLock.release(lockKey, ownerId);
        }
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

    private String lockKey(UUID concertId) {
        return PREFIX + "lock:" + concertId;
    }

    private String memberTokenKey(UUID concertId, UUID memberId) {
        return PREFIX + "member-token:" + concertId + ":" + memberId;
    }

    private String tokenKey(String token) {
        return PREFIX + "token:" + token;
    }
}
