package com.learning.urlshorten.controller;

import com.learning.urlshorten.dto.CreateShortUrlRequest;
import com.learning.urlshorten.dto.CreateShortUrlResponse;
import com.learning.urlshorten.service.counterbase62encode.CounterBase62EncodeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/counter-base62-encode")
@RequiredArgsConstructor
public class CounterBase62EncodeShortUrlController {

    private final Map<String, CounterBase62EncodeService> base62EncodeServices;

    @PostMapping("/shorten")
    public ResponseEntity<?> shorten(@Valid @RequestBody CreateShortUrlRequest req) {
        var response = (CreateShortUrlResponse) base62EncodeServices.get("counter_base62_encode_create").handle(req);
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
