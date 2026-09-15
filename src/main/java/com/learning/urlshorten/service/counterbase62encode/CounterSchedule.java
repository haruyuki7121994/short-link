package com.learning.urlshorten.service.counterbase62encode;

import com.learning.urlshorten.repository.CounterRepository;
import com.learning.urlshorten.repository.RedisCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CounterSchedule {

    @Value("${short-url.counter-name}")
    private String counterName;

    private final RedisCounterRepository redisCounterRepository;
    private final CounterRepository counterRepository;

    @Scheduled(fixedDelayString = "10000")
    public void updateCounter() {
        Long id = redisCounterRepository.get();
        counterRepository.updateByType(counterName, id);
    }
}
