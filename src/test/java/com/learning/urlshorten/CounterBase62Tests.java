package com.learning.urlshorten;

import com.learning.urlshorten.constant.ShortUrlProperties;
import com.learning.urlshorten.dto.CreateShortUrlRequest;
import com.learning.urlshorten.entity.ShortUrlEntity;
import com.learning.urlshorten.repository.CounterRepository;
import com.learning.urlshorten.repository.ShortUrlRepository;
import com.learning.urlshorten.service.counterbase62encode.CounterBase62EncodeCreateShortUrlHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CounterBase62Tests {
    final ShortUrlRepository urls = mock(ShortUrlRepository.class);
    final CounterRepository counter = mock(CounterRepository.class);
    final ShortUrlProperties props = new ShortUrlProperties();
    final CounterBase62EncodeCreateShortUrlHandler handler =
            new CounterBase62EncodeCreateShortUrlHandler(urls, props, counter);
    CounterBase62Tests() { props.setDomain2("https://short.example"); }

    @Test void customAliasBypassesCounter() {
        when(counter.nextId()).thenThrow(new DataAccessResourceFailureException("counter down"));
        var result = handler.handle(new CreateShortUrlRequest("https://example.com", "chosen", null));
        assertEquals("https://short.example/chosen", result.getShortUrl());
        verifyNoInteractions(counter);
        verify(urls).insert(any(ShortUrlEntity.class));
    }
    @Test void duplicateAliasReturnsConflictWithoutAllocatingId() {
        when(urls.insert(any(ShortUrlEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertTrue(handler.handle(new CreateShortUrlRequest("https://example.com", "chosen", null)).isExistedAlias());
        verifyNoInteractions(counter);
    }
    @Test void encodesBoundaryValuesWithoutTruncation() {
        when(counter.nextId()).thenReturn(1L, 61L, 62L, 3844L, Long.MAX_VALUE);
        for (String expected : new String[]{"1", "z", "10", "100", "AzL8n0Y58m7"}) {
            assertEquals("https://short.example/" + expected,
                    handler.handle(new CreateShortUrlRequest("https://example.com", null, null)).getShortUrl());
        }
    }
    @Test void generatedCollisionAllocatesNextIdWithoutSkippingRanges() {
        when(counter.nextId()).thenReturn(61L, 62L);
        when(urls.insert(any(ShortUrlEntity.class))).thenThrow(new DuplicateKeyException("alias occupied"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        assertEquals("https://short.example/10",
                handler.handle(new CreateShortUrlRequest("https://example.com", null, null)).getShortUrl());
        verify(counter, times(2)).nextId();
    }
    @Test void collisionsHaveBoundedRetries() {
        when(counter.nextId()).thenReturn(1L);
        when(urls.insert(any(ShortUrlEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertEquals(503, assertThrows(ResponseStatusException.class, () ->
                handler.handle(new CreateShortUrlRequest("https://example.com", null, null))).getStatusCode().value());
        verify(counter, times(10)).nextId();
    }
    @Test void counterFailureDoesNotInsertMapping() {
        when(counter.nextId()).thenThrow(new DataAccessResourceFailureException("unavailable"));
        assertThrows(DataAccessResourceFailureException.class, () ->
                handler.handle(new CreateShortUrlRequest("https://example.com", null, null)));
        verifyNoInteractions(urls);
    }
}
