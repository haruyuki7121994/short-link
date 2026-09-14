package com.learning.urlshorten.service.base62encode;

import com.learning.urlshorten.entity.ShortUrlEntity;
import com.learning.urlshorten.exception.NotFoundException;
import com.learning.urlshorten.repository.RedisShortUrlRepository;
import com.learning.urlshorten.repository.ShortUrlRepository;
import com.learning.urlshorten.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Service(value = "base62_encode_redirect")
public class Base62EncodeRedirectShortUrlHandler implements Base62EncodeService {

    private final ShortUrlRepository shortUrlRepository;
    private final RedisShortUrlRepository redisShortUrlRepository;

    @Override
    public String handle(Object req) {
        var shortCode = (String) req;
        String cacheKey = "short_url::" + shortCode;

        RedisShortUrlRepository.CacheResult cached = redisShortUrlRepository.readCache(cacheKey);

        if (cached.longUrl() != null) {
            return cached.longUrl();
        }

        // Không tồn tại hoặc hết hạn thì throw NotFoundException
        var entity = findValidMapping(shortCode);

        int jitter = TimeUtil.generateRandomNumber(1, 10);
        Duration ttl;
        if (entity.getExpiresAt() != null) {
            ttl = Duration.between(LocalDateTime.now(), entity.getExpiresAt());
            if (ttl.isZero() || ttl.isNegative()) throw new NotFoundException("ttl is expired");
        } else {
            ttl = Duration.ofMinutes(5).plusSeconds(jitter);
        }

        if (!cached.failed()) {
            redisShortUrlRepository.writeCache(cacheKey, entity.getLongUrl(), ttl);
        }

        return entity.getLongUrl();
    }

    private ShortUrlEntity findValidMapping(String shortCode) {
        return shortUrlRepository.findById(shortCode)
                .orElseThrow(() -> new NotFoundException("short_url not found"));
    }
}
