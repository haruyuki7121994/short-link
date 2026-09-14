package com.learning.urlshorten.service.base62encode;

import com.learning.urlshorten.entity.ShortUrlEntity;
import com.learning.urlshorten.exception.NotFoundException;
import com.learning.urlshorten.repository.RedisShortUrlRepository;
import com.learning.urlshorten.repository.ShortUrlRepository;
import com.learning.urlshorten.util.TimeUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Semaphore;

@Service("base62_encode_redirect")
public class Base62EncodeRedirectShortUrlHandler implements Base62EncodeService {
    private final ShortUrlRepository shortUrlRepository;
    private final RedisShortUrlRepository redisShortUrlRepository;
    private final Semaphore databaseLookups;

    public Base62EncodeRedirectShortUrlHandler(ShortUrlRepository repository, RedisShortUrlRepository cache,
                                               @Value("${short-url.max-concurrent-lookups:64}") int maxLookups) {
        if (maxLookups < 1) throw new IllegalArgumentException("max-concurrent-lookups must be positive");
        this.shortUrlRepository = repository;
        this.redisShortUrlRepository = cache;
        this.databaseLookups = new Semaphore(maxLookups);
    }

    @Override
    public String handle(Object req) {
        String shortCode = (String) req;
        // A new namespace avoids legacy raw strings and SETRANGE-corrupted cache keys.
        String cacheKey = "short_url:v2::" + shortCode;
        var cached = redisShortUrlRepository.readCache(cacheKey);
        if (cached.longUrl() != null) {
            validateExpiration(cached.expiresAt());
            return cached.longUrl();
        }

        if (!databaseLookups.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Lookup capacity exceeded");
        }
        ShortUrlEntity entity;
        try {
            entity = shortUrlRepository.findById(shortCode)
                    .orElseThrow(() -> new NotFoundException("short_url not found"));
        } finally {
            databaseLookups.release();
        }
        validateExpiration(entity.getExpiresAt());
        Duration ttl = Duration.ofMinutes(5).plusSeconds(TimeUtil.generateRandomNumber(1, 10));
        if (entity.getExpiresAt() != null) {
            Duration remaining = Duration.between(LocalDateTime.now(), entity.getExpiresAt());
            if (remaining.isNegative() || remaining.isZero()) throw new NotFoundException("short_url expired");
            if (remaining.compareTo(ttl) < 0) ttl = remaining;
        }
        // Do not incur another Redis timeout after a failed read.
        if (!cached.failed()) {
            redisShortUrlRepository.writeCache(cacheKey, entity.getLongUrl(), entity.getExpiresAt(), ttl);
        }
        return entity.getLongUrl();
    }

    private void validateExpiration(LocalDateTime expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new NotFoundException("short_url expired");
        }
    }
}
