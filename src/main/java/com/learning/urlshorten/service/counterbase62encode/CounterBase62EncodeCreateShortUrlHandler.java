package com.learning.urlshorten.service.counterbase62encode;

import com.learning.urlshorten.constant.ShortUrlProperties;
import com.learning.urlshorten.dto.CreateShortUrlRequest;
import com.learning.urlshorten.dto.CreateShortUrlResponse;
import com.learning.urlshorten.entity.ShortUrlEntity;
import com.learning.urlshorten.repository.RedisCounterRepository;
import com.learning.urlshorten.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Slf4j
@RequiredArgsConstructor
@Service(value = "counter_base62_encode_create")
public class CounterBase62EncodeCreateShortUrlHandler implements CounterBase62EncodeService {

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlProperties shortUrlProperties;
    private final RedisCounterRepository redisCounterRepository;
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    @Override
    public CreateShortUrlResponse handle(Object req) {
        var request = (CreateShortUrlRequest) req;

        boolean isCustomAlias = StringUtils.hasText(request.getCustomAlias());
        if (isCustomAlias && "shorten".equalsIgnoreCase(request.getCustomAlias())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reserved alias");
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            Long id = redisCounterRepository.increment();
            String shortKey = isCustomAlias ? request.getCustomAlias() : getShortKey(id);
            var now = LocalDateTime.now();
            var newEntity = ShortUrlEntity.builder()
                    .shortUrl(shortKey)
                    .longUrl(request.getLongUrl())
                    .expiresAt(request.getExpiration())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            try {
                // Atomic insert: never replace an existing mapping on a collision.
                shortUrlRepository.insert(newEntity);
                return to(newEntity);
            } catch (DuplicateKeyException ex) {
                if (isCustomAlias) {
                    return CreateShortUrlResponse.builder().existedAlias(true).build();
                }
                redisCounterRepository.incrementBy(1000);
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to allocate short code");
    }

    private String getShortKey(long number) {
        StringBuilder sb = new StringBuilder();
        while (number > 0) {
            int index = (int) (number % ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
            number = number / ALPHANUMERIC.length();
        }
        return sb.reverse().toString();
    }

    private CreateShortUrlResponse to(ShortUrlEntity entity) {
        return CreateShortUrlResponse.builder()
                .shortUrl(String.format("%s/%s", shortUrlProperties.getDomain2(), entity.getShortUrl()))
                .expiration(entity.getExpiresAt())
                .build();
    }
}
