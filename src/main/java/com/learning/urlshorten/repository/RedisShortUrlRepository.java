package com.learning.urlshorten.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisShortUrlRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public record CacheResult(String longUrl, boolean failed) {}

    public CacheResult readCache(String key) {
        try {
            return new CacheResult(redisTemplate.opsForValue().get(key), false);
        } catch (DataAccessException ex) {
            log.warn("Redis read failed: {}", ex.getClass().getSimpleName());
            return new CacheResult(null, true);
        }
    }

    public void writeCache(String key, String longUrl, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, longUrl, ttl);
        } catch (DataAccessException ex) {
            log.warn("Redis write failed: {}", ex.getClass().getSimpleName());
            // Đã có kết quả DB hợp lệ: vẫn trả longUrl
        }
    }
}
