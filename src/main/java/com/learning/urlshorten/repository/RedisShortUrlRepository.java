package com.learning.urlshorten.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class RedisShortUrlRepository {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final StringRedisTemplate redisTemplate;
    public record CacheResult(String longUrl, LocalDateTime expiresAt, boolean failed) {}
    public record StoredMapping(String longUrl, String expiresAt) {}

    public CacheResult readCache(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) return new CacheResult(null, null, false);
            var mapping = JSON.readValue(value, StoredMapping.class);
            if (mapping == null || mapping.longUrl() == null) return new CacheResult(null, null, false);
            return new CacheResult(
                    mapping.longUrl(),
                    mapping.expiresAt() == null ? null : LocalDateTime.parse(mapping.expiresAt()),
                    false
            );
        } catch (DataAccessException ex) {
            log.debug("Redis read failed: {}", ex.getClass().getSimpleName());
            return new CacheResult(null, null, true);
        } catch (JacksonException | IllegalArgumentException ex) {
            // Treat malformed cache data as a miss; MongoDB remains authoritative.
            log.debug("Ignoring malformed cached mapping");
            return new CacheResult(null, null, false);
        }
    }

    public void writeCache(String key, String longUrl, LocalDateTime expiresAt, Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) return;
        try {
            var value = new StoredMapping(longUrl, expiresAt == null ? null : expiresAt.toString());
            redisTemplate.opsForValue().set(key, JSON.writeValueAsString(value), ttl);
        } catch (DataAccessException | JacksonException ex) {
            log.debug("Redis write failed: {}", ex.getClass().getSimpleName());
        }
    }
}
