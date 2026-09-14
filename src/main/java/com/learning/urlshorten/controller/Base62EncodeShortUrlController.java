package com.learning.urlshorten.controller;

import com.learning.urlshorten.dto.CreateShortUrlRequest;
import com.learning.urlshorten.dto.CreateShortUrlResponse;
import com.learning.urlshorten.service.base62encode.Base62EncodeService;
import io.vavr.control.Try;
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
        return Try.of(() -> base62EncodeServices.get("base62_encode_create").handle(req)).toEither().fold(
                throwable -> ResponseEntity.internalServerError().body(throwable.getMessage()),
                (value) -> {
                    var response = (CreateShortUrlResponse) value;
                    return response.isExistedAlias() ?
                            ResponseEntity.status(HttpStatus.CONFLICT).body(response) :
                            ResponseEntity.ok().body(response);
                }
        );
    }

    @GetMapping("/{shortUrl}")
    public ResponseEntity<?> redirect(@NotBlank @PathVariable(name = "shortUrl") String shortUrl) {
        return Try.of(() -> base62EncodeServices.get("base62_encode_redirect").handle(shortUrl)).toEither().fold(
                throwable -> ResponseEntity.internalServerError().body(throwable.getMessage()),
                value -> value != null ?
                        ResponseEntity.status(302).location(URI.create((String) value)).build() :
                        ResponseEntity.internalServerError().body(null)
        );
    }
}
