# Counter + Base62 implementation

Generated codes use `CounterRepository.nextId()` followed by full Base62 encoding. Each allocation performs one MongoDB `findOneAndUpdate` with `$inc`, `upsert`, and `returnDocument(AFTER)` on `counter._id = short-url.counter-name`. The first allocation on a fresh database creates a long-valued counter and returns 1. All application instances share this document.

Counter writes request majority acknowledgment and journaling. Use a replica set in production; a standalone local MongoDB does not provide replication or failover. Counter allocation failures propagate as HTTP 503. A write with an uncertain acknowledgment may consume an ID without returning a URL; gaps are acceptable, so do not decrement or reset the counter. Protect and back up this collection together with the mappings. Deleting it or restoring an older backup can cause ID reuse and requires recovery before enabling creates.

Custom aliases bypass ID allocation and go straight to the mapping insert. Generated codes still use an atomic mapping insert, because an existing custom alias or random Base62 code can occupy that name. Conflicts consume a new ID, with at most 10 attempts; they no longer jump the counter by 1,000. Counter overflow fails closed instead of wrapping. IDs remain guessable; Base62 is an encoding, not an authorization mechanism.

There is no Redis counter, startup restoration hook, or periodic counter checkpoint job. Redis remains an optional redirect cache. The application can start with Redis unavailable, and custom-alias creation does not depend on Redis. At higher write volumes, durable range allocation can reduce contention on the counter document; this implementation deliberately allocates one ID per database operation.

## Migration from the Redis counter version

Do not run old and new counter writers simultaneously: an old scheduler can overwrite the new durable counter with a stale value.

1. Stop old counter creates and all old scheduler instances before the cutover.
2. Establish a trusted highest allocated ID from the live Redis counter and durable records. The old MongoDB checkpoint alone can lag or be missing. Include IDs consumed by failed creates or skipped ranges. If the Redis state has already been lost, do not assume zero or the last checkpoint is safe; recover a trustworthy upper bound first. Existing mappings alone cannot prove the maximum if mappings have been deleted.
3. Seed the existing `counter` document using `$max` and `upsert`, with a BSON 64-bit integer and majority/journal acknowledgment, before starting new creates. Preserve a larger existing counter. For example, in mongosh, replacing the placeholder with the verified decimal upper bound:

```javascript
db.counter.updateOne(
  { _id: "counter_short_url" },
  { $max: { counter: NumberLong("VERIFIED_UPPER_BOUND") } },
  { upsert: true, writeConcern: { w: "majority", j: true } }
)
```

4. Start only the new application version. Verify the next allocated ID exceeds the migration bound. Keep the counter name stable across instances and deployments.

A genuinely new database needs no manual seed. This commit does not connect to or migrate existing application data automatically.

## Tests

`./mvnw test` runs unit, HTTP, and Spring wiring tests without external dependencies. The real MongoDB tests are opt-in and create/drop uniquely named `counter_test_*` databases on the supplied server. Point them only at a disposable test MongoDB:

```sh
COUNTER_TEST_MONGODB_URI='mongodb://127.0.0.1:27017/?directConnection=true' ./mvnw test
```

These tests cover first use, concurrent allocation across repository instances, continued allocation after recreating a repository, preservation of an existing counter, overflow, and non-reuse of consumed IDs. They do not simulate replica-set failover or benchmark throughput.
