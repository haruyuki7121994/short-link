package com.learning.urlshorten.repository;

import com.learning.urlshorten.entity.CounterEntity;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.mongodb.core.CollectionCallback;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CounterRepository {
    private final MongoTemplate mongoTemplate;

    @Value("${short-url.counter-name}")
    private String counterName;

    public long nextId() {
        // Concurrent first use can race on upsert; retry the unique-key conflict only.
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Long allocated = mongoTemplate.execute(CounterEntity.class, (CollectionCallback<Long>) collection -> {
                    Document result = collection.withWriteConcern(WriteConcern.MAJORITY.withJournal(true))
                            .findOneAndUpdate(
                                    new Document("_id", counterName)
                                            .append("counter", new Document("$lt", Long.MAX_VALUE)),
                                    new Document("$inc", new Document("counter", 1L)),
                                    new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER));
                    if (result == null || !(result.get("counter") instanceof Long value) || value <= 0) {
                        throw new DataIntegrityViolationException("Invalid counter state");
                    }
                    return (Long) result.get("counter");
                });
                if (allocated == null) throw new DataIntegrityViolationException("No counter was allocated");
                return allocated;
            } catch (DuplicateKeyException ex) {
                // Also fails closed at Long.MAX_VALUE instead of wrapping or resetting.
                if (attempt == 2) {
                    throw new TransientDataAccessResourceException("Counter unavailable or exhausted", ex);
                }
            }
        }
        throw new IllegalStateException("Unreachable counter allocation state");
    }
}
