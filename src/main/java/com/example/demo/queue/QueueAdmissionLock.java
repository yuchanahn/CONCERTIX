package com.example.demo.queue;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class QueueAdmissionLock {

    private static final DefaultRedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    QueueAdmissionLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    boolean acquire(String key, String ownerId, Duration ttl) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, ownerId, ttl));
    }

    void release(String key, String ownerId) {
        redisTemplate.execute(RELEASE_IF_OWNER, List.of(key), ownerId);
    }
}
