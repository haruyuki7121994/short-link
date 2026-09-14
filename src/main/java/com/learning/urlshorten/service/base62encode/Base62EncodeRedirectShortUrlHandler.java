package com.learning.urlshorten.service.base62encode;

import com.learning.urlshorten.repository.ShortUrlRepository;
import com.learning.urlshorten.util.TimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
@Service(value = "base62_encode_redirect")
public class Base62EncodeRedirectShortUrlHandler implements Base62EncodeService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ShortUrlRepository shortUrlRepository;

    @Override
    public String handle(Object req) {
        var request = (String) req;
        String cacheKey = "short_url::" + request;

        // 1. Kiểm tra xem cache đã tồn tại chưa (Cache Hit)
        String longUrl = redisTemplate.opsForValue().get(cacheKey);
        if (longUrl != null) {
            return longUrl;
        }

        // 2. Cache Miss: Gọi Database để lấy dữ liệu
        var entity = shortUrlRepository.findById(request).orElseThrow();

        // 3. Lưu vào Redis với TTL động được truyền từ Request
        redisTemplate.opsForValue().set(
                cacheKey,
                entity.getLongUrl(),
                entity.getExpiresAt() != null ?
                        Duration.ofSeconds(entity.getExpiresAt().getSecond()) :
                        Duration.ofMinutes(5).plusSeconds(TimeUtil.generateRandomNumber(1, 10))
        );

        return entity.getLongUrl();
    }
}
