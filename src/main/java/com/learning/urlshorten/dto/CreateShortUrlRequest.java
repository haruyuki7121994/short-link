package com.learning.urlshorten.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String longUrl;
    private String customAlias;
    private LocalDateTime expiration;
}
