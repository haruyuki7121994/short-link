package com.learning.urlshorten.repository;

import com.learning.urlshorten.entity.CounterEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

@Repository
public interface CounterRepository extends MongoRepository<CounterEntity, String> {

    @Query("{ 'type' : ?0 }")
    @Update("{ '$set' : { 'counter' : ?1 } }")
    void updateByType(String type, Long counter);
}
