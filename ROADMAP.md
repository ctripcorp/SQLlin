# SQLlin Roadmap

Each item has an ID, `Fx`, which stays the same when the item moves to another priority, and under Supported once it is
done. A new item takes the next number.

## Medium Priority

* F8: Support WASM platform DSL
* F14: Support constraints in `ALTER_ADD_COLUMN`, which adds a column with its type only: DEFAULT, NOT NULL with DEFAULT, CHECK, COLLATE NOCASE and REFERENCES
* F15: Support WITHOUT ROWID tables, which save space and a lookup when the primary key isn't an INTEGER; the ORDER BY and LIMIT of UPDATE and DELETE (F25) then have to choose the rows by their primary key, as SQLite does, rather than by the rowid
* F16: Support STRICT tables, which reject values of other types than their columns' (SQLite 3.37.0, Android API 34)
* F21: Support FILTER in aggregate functions (SQLite 3.30.0, Android API 31), and ORDER BY in them, as for `group_concat` (SQLite 3.44.0, Android API 35)

## Low Priority

* F31: Support using a query's results within the same transaction, so that a read-modify-write is one transaction
* F32: Support upsert, `INSERT ... ON CONFLICT (target) DO UPDATE` and `DO NOTHING`, which updates the conflicting row in place where `INSERT OR REPLACE` deletes and re-inserts it
* F33: Use a SQLite bundled with SQLlin on Android, such as androidx.sqlite's sqlite-bundled, rather than the system's, which has no FTS5 or R*Tree, and on older Android versions lacks newer SQL such as DROP COLUMN (before API 34) and upsert (before API 30)
* F34: Support FTS5 and R*Tree virtual tables, which Android's system SQLite lacks
* F35: Support common table expressions, WITH and WITH RECURSIVE, which hierarchical data such as trees needs, in SELECT and in INSERT, UPDATE and DELETE
* F36: Support window functions, with OVER, PARTITION BY and WINDOW (SQLite 3.25.0, Android API 30)
* F37: Support RETURNING in INSERT, UPDATE and DELETE, which returns the rows they write, such as the ids SQLite assigns (SQLite 3.35.0, Android API 34)
* F38: Support savepoints, SAVEPOINT, RELEASE and ROLLBACK TO, for nested transactions
* F39: Support ATTACH and DETACH, to query several databases together
* F40: Support generated columns, `GENERATED ALWAYS AS (...)` (SQLite 3.31.0, Android API 31)
* F41: Support CREATE TABLE ... AS SELECT
* F42: Support TEMP tables and views
* F43: Support custom collations, which the driver registers
* F44: Support VALUES as a query, and SELECT without FROM, such as `SELECT datetime('now')`
* F45: Support table-valued functions in FROM, such as json_each, json_tree and pragma_table_info (the JSON ones need SQLite 3.38.0, Android API 34)
* F47: Support UPDATE ... FROM, which updates rows with the values of another table, and needs the expressions of F9 (SQLite 3.33.0, Android API 34)
* F48: Support virtual tables other than FTS and R*Tree, such as dbstat
* F63: Support INSTEAD OF triggers on views, together with INSERT, UPDATE and DELETE on a view, which the DSL doesn't allow, as a view isn't a `Table`, so that such a trigger can be used; left out of the first version of CREATE TRIGGER (F10)
* F64: Support the JSON functions and JSON's `->` and `->>` operators (SQLite 3.38.0, Android API 34), left out of F46
* F65: Support the conflict clause of NOT NULL, as `NOT NULL ON CONFLICT REPLACE`, which writes the default value instead of NULL, left out of F17: a property's type declares NOT NULL, so no annotation can hold the clause yet; it could be a new annotation, or a parameter of `@Default`. SQLlin's statements rarely write NULL to a non-null column, so it is of little use for now
* F67: Support the PRAGMAs that set how each connection runs statements, such as cache_size, temp_store, mmap_size, query_only, automatic_index, trusted_schema, threads, cache_spill, analysis_limit, journal_size_limit, wal_autocheckpoint, max_page_count, secure_delete and cell_size_check, as parameters of `DatabaseConfiguration` and `DSLDBConfiguration` rather than as statements: Android's framework reads through several connections of a database in WAL mode from Android 13 on, while a statement sets a PRAGMA on the one connection that writes, so they have to be set on every connection, as `busyTimeout` is, and Android's framework sets journal_size_limit and wal_autocheckpoint on each connection it opens; and a change of temp_store deletes the TEMP tables and triggers, which observed queries count changes with

## Shelved

Items that aren't planned, as they are of little use, or would cost more than they give, until a need for them comes up.

* F68: Support `PRAGMA incremental_vacuum`, which removes the unused pages that the INCREMENTAL mode of `PRAGMA_AUTO_VACUUM(mode)` keeps: it removes a page each time it returns a row, which has no columns, so it has to run as a query whose rows are all read. Android's framework and Kotlin/Native would run it so, but sqlite-jdbc doesn't run a statement that returns no columns as a query, and runs it as a statement only as far as its first row, removing one page, so the JVM would have to run `PRAGMA incremental_vacuum(1)` once for each page, in a transaction. Few databases are in INCREMENTAL mode, and Android's SQLite creates them in FULL mode

## Supported

* F12: Support type converters: store a property of any type through a serializer that encodes it to a type SQLite supports, with type-safe WHERE and SET on its column. The serializer kotlinx.serialization writes the property with is the converter: built-in ones of `Uuid`, `Instant` and `Duration`, value classes, and any given with `@Serializable(with = ...)` or `@file:UseSerializers`; the column's element, `ClauseConverted`, converts the values of its conditions with it (2.6.0 ✅)
* F13: Provide a `PagingSource` for androidx.paging built on the table version tokens of F66, like Room's `LimitOffsetPagingSource`, in the new module sqllin-dsl-paging, `Database#pagingSource { }` (2.6.0 ✅)
* F25: Support ORDER BY and LIMIT in UPDATE and DELETE, which SQLite only takes with its `SQLITE_ENABLE_UPDATE_DELETE_LIMIT` option, which Android's lacks: SQLlin writes the subquery of rowids that SQLite turns them into, which every platform runs (2.6.0 ✅)
* F19: Support the collations BINARY and RTRIM besides NOCASE, and COLLATE on the columns of an index and in ORDER BY: `@CollateRTrim` for a column, which needs no `@CollateBinary`, as BINARY is a column's default, and the `COLLATE` operator of F46 for a column of an index, as it already was for conditions and ORDER BY (2.6.0 ✅)
* F30: Support more PRAGMAs: those that check the database, read its schema and what the SQLite has, checkpoint the WAL, read the size and the header of the file, and set how statements run; case_sensitive_like is left out, as SQLite deprecates it, and incremental_vacuum, shelved as F68, as sqlite-jdbc only runs it as far as the first of the rows it returns, one for each page it removes (2.6.0 ✅)
* F29: Support EXPLAIN QUERY PLAN, to see which indexes a query uses (2.6.0 ✅)
* F26: Support INDEXED BY and NOT INDEXED (2.6.0 ✅)
* F27: Support choosing how a transaction begins, DEFERRED, IMMEDIATE or EXCLUSIVE (2.6.0 ✅)
* F23: Support the other conflict resolutions: INSERT OR ABORT, OR FAIL and OR ROLLBACK, and UPDATE OR ABORT, OR FAIL, OR IGNORE, OR REPLACE and OR ROLLBACK (2.6.0 ✅)
* F66: Provide table version tokens, `versioned { }`, `awaitChange` and `onChange`, which tell a reader when the tables it read change (2.6.0 ✅)
* F7: Remove the joins written as clauses of SELECT, such as `table SELECT INNER_JOIN<R>(other)`, and let a join start with its first relation, as in `PersonTable INNER_JOIN BookTable`, without `FROM` (2.6.0 ✅)
* F17: Support conflict clauses on PRIMARY KEY and UNIQUE constraints (2.5.0 ✅)
* F18: Support DEFERRABLE INITIALLY DEFERRED foreign keys (2.5.0 ✅)
* F22: Support NULLS FIRST and NULLS LAST in ORDER BY (2.5.0 ✅)
* F20: Support ASC and DESC on the columns of an index (2.5.0 ✅)
* F28: Support ANALYZE, REINDEX and PRAGMA optimize (2.5.0 ✅)
* F24: Support INSERT ... DEFAULT VALUES (2.5.0 ✅)
* F10: Support CREATE TRIGGER and DROP TRIGGER, with BEFORE and AFTER triggers on tables (2.5.0 ✅)
* F9: Support expressions in UPDATE's SET and in INSERT (2.5.0 ✅)
* F46: Support more expression syntax: CASE, scalar subqueries, COLLATE, bitwise operators and null-safe IS comparisons; REGEXP is left out, as its function differs between the platforms, and the JVM's SQLite has none (2.5.0 ✅)
* F11: Support more functions, CAST, literal values and arithmetic in expressions (2.5.0 ✅)
* F6: Support VACUUM and VACUUM INTO (2.5.0 ✅)
* F5: Support ESCAPE in LIKE (2.5.0 ✅)
* F3: Support DISTINCT in aggregate functions (2.5.0 ✅)
* F4: Support partial indexes, `CREATE INDEX ... WHERE` (2.5.0 ✅)
* F2: Support IF NOT EXISTS and IF EXISTS on CREATE and DROP of tables, views and indexes (2.5.0 ✅)
* F1: Support DROP INDEX (2.5.0 ✅)
* F49: Support joins of relations: checked result types, result columns, more than two tables, and RIGHT and FULL OUTER JOIN (2.5.0 ✅)
* F50: Support subqueries: derived tables in FROM and JOIN, IN and EXISTS (2.5.0 ✅)
* F51: Support CHECK constraints (2.5.0 ✅)
* F52: Support CREATE VIRTUAL TABLE for FTS4 and FTS3 full-text search (2.5.0 ✅)
* F53: Support CREATE VIEW (2.5.0 ✅)
* F54: Support observable queries that return a `Flow` of their results (2.5.0 ✅)
* F55: Support compound SELECTs: UNION, UNION ALL, INTERSECT and EXCEPT (2.5.0 ✅)
* F56: Support INSERT INTO ... SELECT (2.4.0 ✅)
* F57: Support SQL functions in SELECT results (2.4.0 ✅)
* F58: Support SELECT projection (2.4.0 ✅)
* F59: Support INSERT OR IGNORE (2.4.0 ✅)
* F60: Support INSERT OR REPLACE (2.3.0 ✅)
* F61: Support FOREIGN KEY DSL (2.2.0 ✅)
* F62: Support CREATE INDEX DSL (2.2.0 ✅)
