package com.learning.urlshorten;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class UrlShortenApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenApplication.class, args);
    }

}
