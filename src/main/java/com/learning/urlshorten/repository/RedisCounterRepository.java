package com.learning.urlshorten.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RedisCounterRepository {

    private final StringRedisTemplate redisTemplate;

    @Value("${short-url.counter-name}")
    private String counterName;

    public boolean exists() {
        return redisTemplate.hasKey(counterName);
    }

    public Long get() {
        if (exists()) {
            return Long.parseLong(Objects.requireNonNull(redisTemplate.opsForValue().get(counterName)));
        }
        return 0L;
    }

    public void setIfAbsent(String value) {
        redisTemplate.opsForValue().setIfAbsent(counterName, value);
    }

    /**
     * Atomically increments the counter by 1.
     */
    public Long increment() {
        return redisTemplate.opsForValue().increment(counterName);
    }

    /**
     * Atomically increments the counter by a specific delta.
     */
    public Long incrementBy(long delta) {
        return redisTemplate.opsForValue().increment(counterName, delta);
    }

    /**
     * Atomically decrements the counter by 1.
     */
    public Long decrement() {
        return redisTemplate.opsForValue().decrement(counterName);
    }
}
