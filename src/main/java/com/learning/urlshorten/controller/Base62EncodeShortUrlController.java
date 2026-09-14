package com.learning.urlshorten.controller;

import com.learning.urlshorten.dto.CreateShortUrlRequest;
import com.learning.urlshorten.dto.CreateShortUrlResponse;
import com.learning.urlshorten.service.base62encode.Base62EncodeService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/base62-encode")
@RequiredArgsConstructor
public class Base62EncodeShortUrlController {

    private final Map<String, Base62EncodeService> base62EncodeServices;

    @PostMapping("/shorten")
    public ResponseEntity<?> shorten(@Valid @RequestBody CreateShortUrlRequest req) {
        var response = (CreateShortUrlResponse) base62EncodeServices.get("base62_encode_create").handle(req);
        return ResponseEntity.status(response.isExistedAlias() ? HttpStatus.CONFLICT : HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{shortUrl}")
    public ResponseEntity<?> redirect(@NotBlank @PathVariable(name = "shortUrl") String shortUrl) {
        String longUrl = (String) base62EncodeServices
                .get("base62_encode_redirect")
                .handle(shortUrl);

        return ResponseEntity.status(302)
                .location(URI.create(longUrl))
                .header("Cache-Control", "no-store")
                .build();
    }
}
