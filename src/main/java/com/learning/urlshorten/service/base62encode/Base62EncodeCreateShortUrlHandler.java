package com.learning.urlshorten.service.base62encode;

import com.learning.urlshorten.constant.ShortUrlProperties;
import com.learning.urlshorten.dto.CreateShortUrlRequest;
import com.learning.urlshorten.dto.CreateShortUrlResponse;
import com.learning.urlshorten.entity.ShortUrlEntity;
import com.learning.urlshorten.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RequiredArgsConstructor
@Service(value = "base62_encode_create")
public class Base62EncodeCreateShortUrlHandler implements Base62EncodeService {

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlProperties shortUrlProperties;
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public CreateShortUrlResponse handle(Object req) {
        var request = (CreateShortUrlRequest) req;

        AtomicBoolean foundInDb = new AtomicBoolean(true);
        boolean isCustomAlias = StringUtils.hasText(request.getCustomAlias());
        do {
            String shortKey = isCustomAlias ? request.getCustomAlias() : getShortKey(7);
            var entity = shortUrlRepository.findById(shortKey).orElse(null);
            if (entity == null) {
                var newEntity = ShortUrlEntity.builder()
                        .shortUrl(shortKey)
                        .longUrl(request.getLongUrl())
                        .expiresAt(request.getExpiration())
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                shortUrlRepository.insert(newEntity);
                return to(newEntity);
            } else if (isCustomAlias) {
                return CreateShortUrlResponse.builder().existedAlias(true).build();
            } else {
                foundInDb.set(true);
            }
        }
        while (foundInDb.get());
        throw new RuntimeException("Cannot create short link");
    }

    private String getShortKey(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(index));
        }
        return sb.toString();
    }

    private CreateShortUrlResponse to(ShortUrlEntity entity) {
        return CreateShortUrlResponse.builder()
                .shortUrl(String.format("%s/%s", shortUrlProperties.getDomain(), entity.getShortUrl()))
                .expiration(entity.getExpiresAt())
                .build();
    }
}
