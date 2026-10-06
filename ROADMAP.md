# SQLlin Roadmap

## Medium Priority

* Support WASM platform DSL
* Support CREATE VIRTUAL TABLE DSL
* Support CREATE TRIGGER DSL
* Support JOIN sub-query DSL
* Support more functions, such as `coalesce` and `ifnull`, as well as `CAST` and literal values in expressions, e.g. to convert data while copying it to a rebuilt table with `INSERT INTO ... SELECT`
* Support type converters: store a property of any type through a serializer that encodes it to a type SQLite supports, with type-safe WHERE and SET on its column, e.g. to store instances of kotlinx.datetime
* Provide a `PagingSource` for androidx.paging built on observable queries, like Room's `LimitOffsetPagingSource`, so that paging a query doesn't need a hand-written bridge

## Low Priority

* Support CHECK keyword
* Support using a query's results within the same transaction, so that a read-modify-write is one transaction
* Support upsert, `INSERT ... ON CONFLICT (target) DO UPDATE` and `DO NOTHING`, which updates the conflicting row in place where `INSERT OR REPLACE` deletes and re-inserts it

## Supported

* Support CREATE VIEW (2.5.0 ✅)
* Support observable queries that return a `Flow` of their results (2.5.0 ✅)
* Support compound SELECTs: UNION, UNION ALL, INTERSECT and EXCEPT (2.5.0 ✅)
* Support INSERT INTO ... SELECT (2.4.0 ✅)
* Support SQL functions in SELECT results (2.4.0 ✅)
* Support SELECT projection (2.4.0 ✅)
* Support INSERT OR IGNORE (2.4.0 ✅)
* Support INSERT OR REPLACE (2.3.0 ✅)
* Support FOREIGN KEY DSL (2.2.0 ✅)
* Support CREATE INDEX DSL (2.2.0 ✅)