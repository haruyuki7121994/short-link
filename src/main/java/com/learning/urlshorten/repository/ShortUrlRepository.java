package com.learning.urlshorten.repository;

import com.learning.urlshorten.entity.ShortUrlEntity;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShortUrlRepository extends MongoRepository<ShortUrlEntity, String> {

    @Cacheable(value = "short_url")
    ShortUrlEntity getShortUrlEntitiesByShortUrl(String shortUrl);
}
