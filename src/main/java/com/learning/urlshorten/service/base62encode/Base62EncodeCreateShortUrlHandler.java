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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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

        boolean isCustomAlias = StringUtils.hasText(request.getCustomAlias());
        if (isCustomAlias && "shorten".equalsIgnoreCase(request.getCustomAlias())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reserved alias");
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            String shortKey = isCustomAlias ? request.getCustomAlias() : getShortKey(7);
            var now = LocalDateTime.now();
            var newEntity = ShortUrlEntity.builder()
                    .shortUrl(shortKey).longUrl(request.getLongUrl())
                    .expiresAt(request.getExpiration()).createdAt(now).updatedAt(now).build();
            try {
                // Atomic insert: never replace an existing mapping on a collision.
                shortUrlRepository.insert(newEntity);
                return to(newEntity);
            } catch (DuplicateKeyException ex) {
                if (isCustomAlias) {
                    return CreateShortUrlResponse.builder().existedAlias(true).build();
                }
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to allocate short code");
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
