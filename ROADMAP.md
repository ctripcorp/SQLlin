# SQLlin Roadmap

## Medium Priority

* Support WASM platform DSL
* Support expressions in UPDATE's SET and in INSERT, such as `visits = visits + 1` or `updated_at = datetime('now')`, which triggers, upsert's `DO UPDATE SET` and rebuilding a table with converted data all need
* Support CREATE TRIGGER DSL
* Support more functions, such as `coalesce` and `ifnull`, as well as `CAST` and literal values in expressions, e.g. to convert data while copying it to a rebuilt table with `INSERT INTO ... SELECT`
* Support type converters: store a property of any type through a serializer that encodes it to a type SQLite supports, with type-safe WHERE and SET on its column, e.g. to store instances of kotlinx.datetime
* Provide a `PagingSource` for androidx.paging built on observable queries, like Room's `LimitOffsetPagingSource`, so that paging a query doesn't need a hand-written bridge

## Low Priority

* Support using a query's results within the same transaction, so that a read-modify-write is one transaction
* Support upsert, `INSERT ... ON CONFLICT (target) DO UPDATE` and `DO NOTHING`, which updates the conflicting row in place where `INSERT OR REPLACE` deletes and re-inserts it
* Use a SQLite bundled with SQLlin on Android, such as androidx.sqlite's sqlite-bundled, rather than the system's, which has no FTS5 or R*Tree, and on older Android versions lacks newer SQL such as DROP COLUMN (before API 34) and upsert (before API 30)
* Support FTS5 and R*Tree virtual tables, which Android's system SQLite lacks

## Supported

* Support joins of relations: checked result types, result columns, more than two tables, and RIGHT and FULL OUTER JOIN (2.5.0 ✅)
* Support subqueries: derived tables in FROM and JOIN, IN and EXISTS (2.5.0 ✅)
* Support CHECK constraints (2.5.0 ✅)
* Support CREATE VIRTUAL TABLE for FTS4 and FTS3 full-text search (2.5.0 ✅)
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