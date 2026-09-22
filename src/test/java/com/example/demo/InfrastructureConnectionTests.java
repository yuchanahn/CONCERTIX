package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.jdbc.core.JdbcTemplate;

class InfrastructureConnectionTests extends TestInfrastructure {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void connectsToPostgresAndRedis() {
        Integer databaseResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        String redisResult;
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            redisResult = connection.ping();
        }

        assertThat(databaseResult).isEqualTo(1);
        assertThat(redisResult).isEqualTo("PONG");
    }
}
