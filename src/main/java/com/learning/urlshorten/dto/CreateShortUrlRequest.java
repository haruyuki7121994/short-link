package com.learning.urlshorten.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;
import com.learning.urlshorten.validation.HttpUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateShortUrlRequest {

    @NotNull
    @NotBlank
    @HttpUrl
    @Size(max = 8192)
    private String longUrl;

    @Pattern(
            regexp = "^[A-Za-z0-9_-]+$",
            message = "customAlias can only contain alphanumeric characters, underscores, and hyphens"
    )
    @Size(max = 64)
    private String customAlias;

    @Future
    private LocalDateTime expiration;
}
