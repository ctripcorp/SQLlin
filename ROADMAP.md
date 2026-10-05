# SQLlin Roadmap

## High Priority

* Support observable queries that return a `Flow` re-emitting whenever the tables they read change, e.g. to invalidate a Paging source
* Support UNION of SELECTs whose result type isn't the table's row type, such as projections, result columns and joins. `UNION` is typed by the table's row type but reads the rows into its first SELECT's result type, so using the results of such a union fails with a `ClassCastException`

## Medium Priority

* Support WASM platform DSL
* Support CREATE VIRTUAL TABLE DSL
* Support CREATE VIEW DSL
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

* Support INSERT INTO ... SELECT (2.4.0 ✅)
* Support SQL functions in SELECT results (2.4.0 ✅)
* Support SELECT projection (2.4.0 ✅)
* Support INSERT OR IGNORE (2.4.0 ✅)
* Support INSERT OR REPLACE (2.3.0 ✅)
* Support FOREIGN KEY DSL (2.2.0 ✅)
* Support CREATE INDEX DSL (2.2.0 ✅)