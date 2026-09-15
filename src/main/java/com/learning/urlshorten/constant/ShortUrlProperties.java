package com.learning.urlshorten.constant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "short-url")
public class ShortUrlProperties {
    private String domain;
    private String domain2;
}
