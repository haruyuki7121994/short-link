db.dropDatabase();
print("Existing database cleared!");

// 2. Explicitly re-create the collection (table)
db.createCollection("url_mappings");
db.createCollection("event_logs");
print("Database re-initialization completed successfully!");