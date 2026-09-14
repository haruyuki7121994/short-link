package com.learning.urlshorten;

import com.learning.urlshorten.constant.ShortUrlProperties;
import com.learning.urlshorten.controller.Base62EncodeShortUrlController;
import com.learning.urlshorten.dto.CreateShortUrlRequest;
import com.learning.urlshorten.entity.ShortUrlEntity;
import com.learning.urlshorten.exception.*;
import com.learning.urlshorten.repository.*;
import com.learning.urlshorten.service.base62encode.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShortUrlRegressionTests {
    final ShortUrlRepository db=mock(ShortUrlRepository.class);
    final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    final ValueOperations<String,String> values=mock(ValueOperations.class);
    final RedisShortUrlRepository cache=new RedisShortUrlRepository(redis);
    final Base62EncodeRedirectShortUrlHandler redirect=new Base62EncodeRedirectShortUrlHandler(db,cache,2);
    final ShortUrlProperties props=new ShortUrlProperties();
    final Base62EncodeCreateShortUrlHandler create=new Base62EncodeCreateShortUrlHandler(db,props);
    final String code="abc1234", url="https://example.com/a?b=1#section", key="short_url:v2::abc1234";
    ShortUrlRegressionTests() { when(redis.opsForValue()).thenReturn(values);props.setDomain("https://short.example"); }
    ShortUrlEntity entity(LocalDateTime expires) {
        return ShortUrlEntity.builder().shortUrl(code).longUrl(url).expiresAt(expires).build();
    }
    MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new Base62EncodeShortUrlController(Map.of(
            "base62_encode_create",create,"base62_encode_redirect",redirect)))
            .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    @Test void redisReadFailureSkipsWriteButReturnsDatabaseMapping() {
        when(values.get(key)).thenThrow(new RedisConnectionFailureException("down"));
        when(db.findById(code)).thenReturn(Optional.of(entity(null)));
        assertEquals(url,redirect.handle(code));
        verify(values,never()).set(anyString(),anyString(),any(Duration.class));
    }
    @Test void cacheWriteFailureDoesNotFailRedirect() {
        when(db.findById(code)).thenReturn(Optional.of(entity(null)));
        doThrow(new RedisConnectionFailureException("down")).when(values).set(anyString(),anyString(),any(Duration.class));
        assertEquals(url,redirect.handle(code));
    }
    @Test void writesDurationTtlAndStoresExpiration() {
        var expires=LocalDateTime.now().plusSeconds(90);
        when(db.findById(code)).thenReturn(Optional.of(entity(expires)));
        redirect.handle(code);
        verify(values).set(eq(key),contains(expires.toString()),argThat((Duration d)->d.toMillis()>0 && d.toSeconds()<=90));
        verify(values,never()).set(anyString(),anyString(),anyLong());
    }
    @Test void longLivedMappingUsesBoundedCacheTtl() {
        when(db.findById(code)).thenReturn(Optional.of(entity(LocalDateTime.now().plusDays(30))));
        redirect.handle(code);
        verify(values).set(eq(key),anyString(),argThat((Duration d)->d.toSeconds()>=301 && d.toSeconds()<=310));
    }
    @Test void expiredDatabaseEntryReturns404WithoutCaching() throws Exception {
        when(db.findById(code)).thenReturn(Optional.of(entity(LocalDateTime.now().minusDays(1))));
        mvc().perform(get("/api/v1/base62-encode/"+code)).andExpect(status().isNotFound());
        verify(values,never()).set(anyString(),anyString(),any(Duration.class));
    }
    @Test void expiredCacheHitDoesNotRedirect() {
        when(values.get(key)).thenReturn("{\"longUrl\":\"https://example.com\",\"expiresAt\":\"2020-01-01T00:00:00\"}");
        assertThrows(NotFoundException.class,()->redirect.handle(code));
        verifyNoInteractions(db);
    }
    @Test void cacheHitReturnsLocationWithoutDatabaseLookup() throws Exception {
        when(values.get(key)).thenReturn("{\"longUrl\":\"https://example.com\",\"expiresAt\":null}");
        mvc().perform(get("/api/v1/base62-encode/"+code)).andExpect(status().isFound())
            .andExpect(header().string("Location","https://example.com"))
            .andExpect(header().string("Cache-Control","no-store"));
        verifyNoInteractions(db);
    }
    @Test void malformedCacheFallsBackAndIsReplaced() {
        when(values.get(key)).thenReturn("broken json");
        when(db.findById(code)).thenReturn(Optional.of(entity(null)));
        assertEquals(url,redirect.handle(code));
        verify(values).set(eq(key),anyString(),any(Duration.class));
    }
    @Test void unknownMappingReturns404() throws Exception {
        when(db.findById(code)).thenReturn(Optional.empty());
        mvc().perform(get("/api/v1/base62-encode/"+code)).andExpect(status().isNotFound());
    }
    @Test void databaseFailureReturns503WithoutLeakingMessage() throws Exception {
        when(db.findById(code)).thenThrow(new DataAccessResourceFailureException("private DB address"));
        mvc().perform(get("/api/v1/base62-encode/"+code)).andExpect(status().isServiceUnavailable())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private DB"))));
    }
    @Test void createWithoutAliasReturns201() throws Exception {
        mvc().perform(post("/api/v1/base62-encode/shorten").contentType("application/json")
            .content("{\"longUrl\":\"https://example.com/path?a=1#fragment\"}"))
            .andExpect(status().isCreated());
        verify(db).insert(any(ShortUrlEntity.class));
        verify(db,never()).save(any(ShortUrlEntity.class));
        verify(db,never()).findById(anyString());
    }
    @Test void aliasCollisionReturns409() throws Exception {
        when(db.insert(any(ShortUrlEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        mvc().perform(post("/api/v1/base62-encode/shorten").contentType("application/json")
            .content("{\"longUrl\":\"https://example.com\",\"customAlias\":\"chosen\"}"))
            .andExpect(status().isConflict());
        verify(db,times(1)).insert(any(ShortUrlEntity.class));
    }
    @Test void randomCollisionRetriesAndPreservesExpiration() {
        when(db.insert(any(ShortUrlEntity.class))).thenThrow(new DuplicateKeyException("duplicate"))
            .thenAnswer(invocation->invocation.getArgument(0));
        var expiry=LocalDateTime.now().plusDays(1);
        var result=create.handle(new CreateShortUrlRequest(url,null,expiry));
        assertEquals(expiry,result.getExpiration());
        verify(db,times(2)).insert(any(ShortUrlEntity.class));
    }
    @Test void collisionRetriesAreBounded() {
        when(db.insert(any(ShortUrlEntity.class))).thenThrow(new DuplicateKeyException("duplicate"));
        var ex=assertThrows(ResponseStatusException.class,()->create.handle(new CreateShortUrlRequest(url,null,null)));
        assertEquals(503,ex.getStatusCode().value());
        verify(db,times(10)).insert(any(ShortUrlEntity.class));
    }
    @Test void validatesDestinationAndOptionalAlias() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            for(String good:List.of(url,"http://localhost:8080/a","https://example.com"))
                assertTrue(validator.validate(new CreateShortUrlRequest(good,null,null)).isEmpty(),good);
            for(String bad:List.of("abc","ftp://example.com","https:///abc","https://example.com/a b","https://example.com:99999"))
                assertFalse(validator.validate(new CreateShortUrlRequest(bad,null,null)).isEmpty(),bad);
            assertFalse(validator.validate(new CreateShortUrlRequest(url,"bad/alias",null)).isEmpty());
            assertFalse(validator.validate(new CreateShortUrlRequest(url,"",null)).isEmpty());
            assertFalse(validator.validate(new CreateShortUrlRequest(url,null,LocalDateTime.now().minusDays(1))).isEmpty());
        }
    }
    @Test void invalidRequestReturns400() throws Exception {
        mvc().perform(post("/api/v1/base62-encode/shorten").contentType("application/json")
            .content("{\"longUrl\":\"abc\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(db);
    }
    @Test void concurrentCreatesCannotBothClaimAlias() throws Exception {
        var stored=new ConcurrentHashMap<String,ShortUrlEntity>();
        var barrier=new CyclicBarrier(2);
        when(db.insert(any(ShortUrlEntity.class))).thenAnswer(invocation->{
            ShortUrlEntity value=invocation.getArgument(0);
            barrier.await(2,TimeUnit.SECONDS);
            if(stored.putIfAbsent(value.getShortUrl(),value)!=null) throw new DuplicateKeyException("duplicate");
            return value;
        });
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->create.handle(new CreateShortUrlRequest(url,code,null)));
            var b=pool.submit(()->create.handle(new CreateShortUrlRequest("https://other.example",code,null)));
            assertNotEquals(a.get(3,TimeUnit.SECONDS).isExistedAlias(),b.get(3,TimeUnit.SECONDS).isExistedAlias());
            assertEquals(1,stored.size());
        }
    }
    @Test void overloadFailsFastAndReleasesLookupPermit() throws Exception {
        var limited=new Base62EncodeRedirectShortUrlHandler(db,cache,1);
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(db.findById(code)).thenAnswer(invocation->{started.countDown();release.await(2,TimeUnit.SECONDS);return Optional.of(entity(null));});
        try(var pool=Executors.newSingleThreadExecutor()) {
            var first=pool.submit(()->limited.handle(code));
            assertTrue(started.await(2,TimeUnit.SECONDS));
            try {
                assertEquals(503,assertThrows(ResponseStatusException.class,()->limited.handle(code)).getStatusCode().value());
            } finally {release.countDown();}
            assertEquals(url,first.get(3,TimeUnit.SECONDS));
            assertEquals(url,limited.handle(code));
        }
    }
}
