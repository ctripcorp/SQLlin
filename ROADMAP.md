# SQLlin Roadmap

## High Priority

* Support observable queries that return a `Flow` re-emitting whenever the tables they read change, e.g. to invalidate a Paging source

## Medium Priority

* Support WASM platform DSL
* Support CREATE VIRTUAL TABLE DSL
* Support CREATE VIEW DSL
* Support CREATE TRIGGER DSL
* Support JOIN sub-query DSL
* Support more functions
* Support type converters: store a property of any type through a serializer that encodes it to a type SQLite supports, with type-safe WHERE and SET on its column, e.g. to store instances of kotlinx.datetime

## Low Priority

* Support CHECK keyword
* Support using a query's results within the same transaction, so that a read-modify-write is one transaction
* Support upsert, `INSERT ... ON CONFLICT (target) DO UPDATE` and `DO NOTHING`, which updates the conflicting row in place where `INSERT OR REPLACE` deletes and re-inserts it

## Supported

* Support SQL functions in SELECT results (2.4.0 ✅)
* Support SELECT projection (2.4.0 ✅)
* Support INSERT OR IGNORE (2.4.0 ✅)
* Support INSERT OR REPLACE (2.3.0 ✅)
* Support FOREIGN KEY DSL (2.2.0 ✅)
* Support CREATE INDEX DSL (2.2.0 ✅)