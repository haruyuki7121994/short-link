package com.learning.urlshorten;

import com.learning.urlshorten.repository.ShortUrlRepository;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class UrlShortenApplicationTests {
    // Verify Spring wiring without connecting to developer or cloud databases.
    @MockitoBean MongoClient mongoClient;
    @MockitoBean ShortUrlRepository repository;
    @MockitoBean StringRedisTemplate redis;
    @Test void contextLoads() {}
}
