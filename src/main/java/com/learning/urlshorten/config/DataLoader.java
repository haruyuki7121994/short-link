package com.learning.urlshorten.config;

import com.learning.urlshorten.repository.CounterRepository;
import com.learning.urlshorten.repository.RedisCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final RedisCounterRepository redisCounterRepository;
    private final CounterRepository counterRepository;

    @Value("${short-url.counter-name}")
    private String counterName;

    @Override
    public void run(String... args) throws Exception {
        if (!redisCounterRepository.exists()) {
            counterRepository.findById(counterName)
                    .ifPresent(counter ->
                            redisCounterRepository.setIfAbsent(String.valueOf(counter.getCounter()))
                    );
        }
    }
}
