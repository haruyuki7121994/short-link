package com.learning.urlshorten;

import com.learning.urlshorten.repository.CounterRepository;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

// Explicit opt-in: this creates/drops only a unique test database on the provided server.
@EnabledIfEnvironmentVariable(named = "COUNTER_TEST_MONGODB_URI", matches = ".+")
class CounterMongoIntegrationTests {
    MongoClient client;
    MongoTemplate mongo;
    CounterRepository first;
    final String name = "counter_short_url";

    CounterRepository repository() {
        var repo = new CounterRepository(mongo);
        ReflectionTestUtils.setField(repo, "counterName", name);
        return repo;
    }
    @BeforeEach void setup() {
        client = MongoClients.create(System.getenv("COUNTER_TEST_MONGODB_URI"));
        mongo = new MongoTemplate(client, "counter_test_" + UUID.randomUUID().toString().replace("-", ""));
        first = repository();
    }
    @AfterEach void cleanup() {
        if (mongo != null) mongo.getDb().drop();
        if (client != null) client.close();
    }
    @Test void firstUseCreatesCounterAndRestartsContinueSequence() {
        assertEquals(1, first.nextId());
        assertEquals(2, first.nextId());
        assertEquals(3, repository().nextId());
        assertEquals(3L, mongo.getCollection("counter").find(new Document("_id", name)).first().getLong("counter"));
    }
    @Test void concurrentInstancesAllocateDistinctIdsFromEmptyCounter() throws Exception {
        var second = repository();
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                var repo = i % 2 == 0 ? first : second;
                tasks.add(pool.submit(repo::nextId));
            }
            Set<Long> ids = new HashSet<>();
            for (var task : tasks) assertTrue(ids.add(task.get(20, TimeUnit.SECONDS)));
            assertEquals(200, ids.size());
            assertEquals(1L, Collections.min(ids));
            assertEquals(200L, Collections.max(ids));
        }
    }
    @Test void preservesExistingLongCheckpoint() {
        mongo.getCollection("counter").insertOne(new Document("_id", name).append("counter", 100000L));
        assertEquals(100001, first.nextId());
    }
    @Test void exhaustedCounterNeverWrapsOrResets() {
        mongo.getCollection("counter").insertOne(new Document("_id", name).append("counter", Long.MAX_VALUE));
        assertThrows(DataAccessException.class, first::nextId);
        assertEquals(Long.MAX_VALUE, mongo.getCollection("counter").find(new Document("_id", name)).first().getLong("counter"));
    }
    @Test void consumedIdIsNotReusedWhenCallerDoesNotSaveMapping() {
        long abandoned = first.nextId();
        assertEquals(abandoned + 1, repository().nextId());
    }
}
