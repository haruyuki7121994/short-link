package com.learning.urlshorten.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "url_mappings")
public class ShortUrlEntity {

    @Id
    @Field(name = "short_url")
    private String shortUrl;
    @Field(name = "long_url")
    private String longUrl;
    @Field(name = "expires_at")
    private LocalDateTime expiresAt;
    @Field(name = "created_at")
    private LocalDateTime createdAt;
    @Field(name = "updated_at")
    private LocalDateTime updatedAt;
}
