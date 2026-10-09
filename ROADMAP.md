# SQLlin Roadmap

Each item has an ID, `Fx`, which stays the same when the item moves to another priority, and under Supported once it is
done. A new item takes the next number.

## High Priority

* F7: Remove the joins written as clauses of SELECT, such as `table SELECT INNER_JOIN<R>(other)`, in the next version after 2.5.0, then let a join start with its first relation, as in `PersonTable INNER_JOIN BookTable`, without `FROM`

## Medium Priority

* F8: Support WASM platform DSL
* F9: Support expressions in UPDATE's SET and in INSERT, such as `visits = visits + 1` or `updated_at = datetime('now')`, which triggers, upsert's `DO UPDATE SET` and rebuilding a table with converted data all need
* F10: Support CREATE TRIGGER DSL
* F12: Support type converters: store a property of any type through a serializer that encodes it to a type SQLite supports, with type-safe WHERE and SET on its column, e.g. to store instances of kotlinx.datetime
* F13: Provide a `PagingSource` for androidx.paging built on observable queries, like Room's `LimitOffsetPagingSource`, so that paging a query doesn't need a hand-written bridge
* F14: Support constraints in `ALTER_ADD_COLUMN`, which adds a column with its type only: DEFAULT, NOT NULL with DEFAULT, CHECK, COLLATE NOCASE and REFERENCES
* F15: Support WITHOUT ROWID tables, which save space and a lookup when the primary key isn't an INTEGER
* F16: Support STRICT tables, which reject values of other types than their columns' (SQLite 3.37.0, Android API 34)
* F17: Support conflict clauses on PRIMARY KEY, UNIQUE and NOT NULL constraints, such as `UNIQUE ON CONFLICT REPLACE`
* F18: Support DEFERRABLE INITIALLY DEFERRED foreign keys, which are checked when the transaction commits, as rows referring to each other need
* F19: Support the collations BINARY and RTRIM besides NOCASE, and COLLATE on the columns of an index and in ORDER BY
* F20: Support ASC and DESC on the columns of an index
* F21: Support FILTER in aggregate functions (SQLite 3.30.0, Android API 31), and ORDER BY in them, as for `group_concat` (SQLite 3.44.0, Android API 35)
* F22: Support NULLS FIRST and NULLS LAST in ORDER BY (SQLite 3.30.0, Android API 31)
* F23: Support the other conflict resolutions: INSERT OR ABORT, OR FAIL and OR ROLLBACK, and UPDATE OR IGNORE, OR REPLACE and the others
* F24: Support INSERT ... DEFAULT VALUES
* F25: Support ORDER BY and LIMIT in UPDATE and DELETE, which need SQLite's `SQLITE_ENABLE_UPDATE_DELETE_LIMIT` option, which Android's lacks
* F26: Support INDEXED BY and NOT INDEXED
* F27: Support choosing how a transaction begins, DEFERRED, IMMEDIATE or EXCLUSIVE, which differs between the platforms now
* F28: Support ANALYZE, REINDEX and PRAGMA optimize (PRAGMA optimize needs SQLite 3.18.0, Android API 26)
* F29: Support EXPLAIN QUERY PLAN, to see which indexes a query uses
* F30: Support more PRAGMAs, such as integrity_check, quick_check, table_info, index_list, wal_checkpoint, defer_foreign_keys and case_sensitive_like

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

## Supported

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
