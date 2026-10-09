# SQLlin Change Log

- Date format: YYYY-MM-dd

## 2.5.0 / 2026-xx-xx

### All

* Rebuild the SQLite static libraries in `libs`, which the native tests on Linux and Windows link, from SQLite 3.51.1 with FTS3, FTS4 (with the enhanced query syntax), FTS5, R*Tree, Geopoly, the math functions, `dbstat` and column metadata enabled, and without debug information, which held paths of the machine that built them

### sqllin-dsl

* New API: the opt-in annotation `@PlatformDependentSQLiteAPI`, which marks the APIs that only work with some SQLite versions or compile-time options, which some platforms SQLlin supports don't have, such as Android's system SQLite at lower API levels. Using one is a compile error unless it is accepted with `@OptIn(PlatformDependentSQLiteAPI::class)`
* **Breaking change**: `RENAME_COLUMN`, which needs SQLite 3.25.0 (Android API 30), and `DROP_COLUMN`, which needs SQLite 3.35.0 (Android API 34), are marked with `@PlatformDependentSQLiteAPI`, so code using them has to opt in
* New experimental DSL API: joins of relations. `FROM(relation)` starts a join, which `INNER_JOIN`, `LEFT_OUTER_JOIN`, `RIGHT_OUTER_JOIN`, `FULL_OUTER_JOIN`, `CROSS_JOIN` and the NATURAL joins extend, each with its own `ON` or `USING`, as in `(FROM(PersonTable) LEFT_OUTER_JOIN BookTable ON (PersonTable.id EQ BookTable.authorId)) SELECT listOf(...)`. A join is selected from with a projection, a clause function such as `WHERE<R>(...)`, or result columns, which can now select from a join, and the type its rows are read into is checked against the joined relations when the statement is built: every property has to name a column of one of them, of a single one unless USING or a NATURAL join merges them, and has to be nullable where an outer join can leave its column or expression NULL. Columns are written with their relations' names, and an observed query of a join watches all its relations. `RIGHT_OUTER_JOIN`, `FULL_OUTER_JOIN` and their NATURAL forms need SQLite 3.39.0 (Android API 34), so they are marked with `@PlatformDependentSQLiteAPI`
* The joins written as clauses of SELECT, such as `table SELECT INNER_JOIN<R>(other) ON (...)`, which don't check the type their rows are read into, will be removed in the next version after 2.5.0: use the joins of relations instead
* `CREATE_INDEX` and `CREATE_UNIQUE_INDEX` document that a function of the table's columns makes an expression index, as `lower(email)` for case-insensitive lookups or uniqueness, and reject a column or function of another table
* New experimental DSL API: `IF NOT EXISTS` and `IF EXISTS`, with `CREATE_IF_NOT_EXISTS`, `CREATE_INDEX_IF_NOT_EXISTS`, `CREATE_UNIQUE_INDEX_IF_NOT_EXISTS`, `CREATE_VIEW_IF_NOT_EXISTS`, `DROP_IF_EXISTS` and `DROP_INDEX_IF_EXISTS`, which do nothing when the table, FTS table, index or view exists, or doesn't, so that a migration step can run more than once
* New experimental DSL API: `DROP_INDEX(name)`, for SQL syntax `DROP INDEX`, which drops an index that `CREATE_INDEX` or `CREATE_UNIQUE_INDEX` created. The guide to modifying the database now describes indexes
* New experimental DSL API: partial indexes. `WHERE` after `CREATE_INDEX`, `CREATE_UNIQUE_INDEX` or their `IF_NOT_EXISTS` forms indexes only the rows that satisfy a condition, as in `UserTable.CREATE_UNIQUE_INDEX("idx_user_email", UserTable.email) WHERE (UserTable.isDeleted IS false)`. SQLite allows neither parameters nor subqueries there, so the values of the condition are written into the SQL, and a subquery is rejected with an `IllegalArgumentException`. To support it, these functions now return an `IndexStatement`, where they returned `Unit`; this is source-compatible, but a library compiled against an earlier version has to be recompiled
* New experimental DSL API: `DISTINCT(element)`, for aggregate functions of the distinct values, as in `count(DISTINCT(author))` for `count(DISTINCT book.author)`. `count`, `avg`, `sum` and `group_concat` take it, and `group_concat` separates the distinct values with commas, as SQLite allows no other separator with DISTINCT. What it returns, a `Distinct`, can't be used anywhere else, which is checked at compile time
* New experimental DSL API: `ESCAPE` after `LIKE`, for SQL syntax `LIKE pattern ESCAPE character`, as in `name LIKE "100\\%%" ESCAPE '\\'`, after which `%`, `_` and the escape character match only themselves. To support it, `LIKE` now returns a `LikeCondition`, which is a `SelectCondition`; this is source-compatible, but a library compiled against an earlier version has to be recompiled
* New experimental DSL API: `VACUUM()` and `VACUUM_INTO(file)`, for SQL syntax `VACUUM` and `VACUUM INTO`, which rebuild the database file into the minimum disk space, or write a vacuumed copy of the database to another file, a consistent backup of a database in use. Both throw an `IllegalStateException` inside a transaction, where SQLite can't vacuum. `VACUUM_INTO` needs SQLite 3.27.0 (Android API 30), so it is marked with `@PlatformDependentSQLiteAPI`
* New experimental DSL API: subqueries. `select AS SomeView` makes a SELECT a derived table, `(SELECT ...) AS name`, named after the object generated for a `@DBView` class, which gives it its name and column properties, though no such view has to exist in the database. A derived table is read like a table, in the FROM clause and in joins, which can join a table with itself through a derived table of its rows. `IN` takes a SELECT of a single column of values of the same kind, which is checked when the statement is built, and `EXISTS(select)` checks whether a SELECT returns any row; it can refer to the columns of the query it is in. They also work in the WHERE of UPDATE and DELETE. Parameters are bound in the order the SQL has them, and an observed query watches the tables its subqueries read
* New experimental DSL API: `NOT(condition)`, which negates a condition in parentheses, as in `NOT(EXISTS(select))`
* New experimental API: the repeatable annotation `@Check`, which adds a `CHECK` constraint with an SQL expression every row has to satisfy: on a property, to its column, and on a `@DBRow` class, to the table. `constraintName` names it, which SQLite reports when a row fails it; SQLite before 3.34, as on Android below API 34, reports an unnamed one by the table's name. The processor rejects an empty expression, a name that isn't an SQL identifier, and two constraints of a table with the same name, and views and FTS tables reject `@Check`
* New experimental DSL API: FTS4 and FTS3 full-text search tables, which SQLite has on every platform SQLlin supports, Android's included. A `@DBRow` class annotated with `@Fts4` or `@Fts3` gets a generated `FtsTable` object, which `CREATE` creates with `CREATE VIRTUAL TABLE`. Its columns are the class's `String` properties, and a `@PrimaryKey` property of type `Long` or `Long?` named `rowid` or `docid` reads and writes the rowid. `@Fts4` takes the tokenizer and its arguments, the prefix lengths to index and the columns not to index, and `@Fts3` the tokenizer and its arguments. `MATCH` searches the table or one of its columns, and `snippet`, `offsets` and `matchinfo` describe the matches. An observed query of an FTS table counts its changes from SQLlin's INSERT, UPDATE and DELETE on it, as a virtual table can't have triggers. `withName` on an FTS table returns an FTS table, so it is rebuilt as a table is
* New experimental DSL API: views. A class annotated with `@DBView` gets a generated view object, such as `AdultView` for `Adult`, which `CREATE_VIEW(AdultView) AS (select)` creates from a SELECT of its row type, checked at compile time, and `DROP(AdultView)` drops. SQLite doesn't let a view take parameters, so the SELECT's values are written into the view's SQL. A view is queried like a table, in joins and compound SELECTs too, and an observed query of it watches the tables it reads, which SQLite lists through `EXPLAIN`. To keep views from being written to, the new `Relation` is the base of `Table` and `View`: SELECT, joins, the SQL functions and the clause elements take a `Relation`, while INSERT, UPDATE, DELETE, CREATE and ALTER keep taking a `Table`. This is source-compatible, but on Kotlin/Native a library compiled against an earlier version has to be recompiled
* New experimental API: `Database#observe`, which turns a SELECT into a `Flow` of its results, emitted when it is collected and again whenever a statement run through the database changes a table the SELECT reads, if the results differ. A join or a compound SELECT watches all of its tables. The changes are counted by SQLite itself, through TEMP triggers on the observed tables, so rows changed by foreign key actions count, while rolled back transactions and statements that match no rows don't. Changes made through another `Database` instance, connection or process aren't seen. Using it needs `@OptIn(ExperimentalDSLDatabaseAPI::class)`
* `kotlinx.coroutines` is now an `api` dependency of `sqllin-dsl`, as `Database#observe` returns a `Flow`
* A `Database` creates the tracker of observed queries when it observes its first query, so that an app that observes none doesn't reach its code, which code shrinkers then remove: on Kotlin/Native, a release executable of an app that only inserts, queries, updates and deletes is about 418 KB smaller, and with R8, the code of `sqllin-dsl` it keeps is a quarter smaller
* New experimental DSL API: compound SELECTs, with `UNION`, `UNION_ALL`, `INTERSECT` and `EXCEPT` as infix operators between two SELECTs of the same result type, as in `(table SELECT WHERE(age LT 18)) UNION (table SELECT WHERE(age GTE 65))`. The result type is checked at compile time, so the SELECTs can combine the rows of different tables through projections, result columns or joins. Chained operators are evaluated from left to right, as in SQL, and parentheses group them. A SELECT that ends with ORDER BY or LIMIT keeps them to itself, and the compound can be followed by its own ORDER BY and LIMIT. Using it needs `@OptIn(ExperimentalDSLDatabaseAPI::class)`
* `DSLDBConfiguration`, `Database(DSLDBConfiguration)` and the DSL APIs `CREATE`, `CREATE_INDEX`, `CREATE_UNIQUE_INDEX`, `DROP`, `ALTER_ADD_COLUMN`, `ALTER_RENAME_TABLE_TO`, `RENAME_COLUMN`, `DROP_COLUMN` and `PRAGMA_FOREIGN_KEYS` are no longer experimental, so using them no longer needs `@OptIn(ExperimentalDSLDatabaseAPI::class)`
* Deprecated: the `UNION {}` and `UNION_ALL {}` blocks, and the `DatabaseScope#beginUnion`, `createUnionSelectStatement` and `endUnion` functions they use. A block returns the table's row type but reads the rows into its first SELECT's result type, so a union of projections, result columns or joins fails with a `ClassCastException` when its results are used, and a nested block is flattened into the SQL, which changes its meaning unless it is the block's first statement. These bugs won't be fixed: use the infix operators instead. The blocks will be removed in a future version
* Fix: a column given to a function, to GROUP BY or to ORDER BY, or selected into a property with `AS`, was written without its table's name, so in a join of tables that both have a column of that name, SQLite failed with "ambiguous column name". Such columns are now written with their tables' names, as conditions already were, except in the ORDER BY of a compound SELECT, which can only name the columns of its results
* Fix: AND and OR combined conditions without parentheses. They apply in the order they are written, as Kotlin's infix functions do, so `(age EQ 1) OR (age EQ 2) AND (name EQ "x")` means `(age = 1 OR age = 2) AND name = 'x'`, but SQL gives AND precedence over OR, so SQLite read it as `age = 1 OR (age = 2 AND name = 'x')` and returned other rows. A condition that combines others with the other operator is now put in parentheses
* Fix: combining a condition with AND or OR added the other condition's parameters to its own, so a condition kept in a variable and used again, alone or combined, bound parameters its SQL doesn't have, which failed, as with an `ArrayIndexOutOfBoundsException` on the JVM. Combining conditions now leaves their parameters as they were
* Fix: unsigned values were stored inconsistently. INSERT stored a `UByte`, `UShort` or `UInt` as the bits of a signed number on every platform, UPDATE did so on the JVM, but stored it as its number on Kotlin/Native and Android, so a `UByte` of 200 read back as 200, but compared, ordered and summed as -56 or as 200, depending on how it was written. They are now always stored as their numbers, which SQLite's 64-bit integers hold, so that SQL compares, orders and sums them as Kotlin does, as `sum` and `max` of such a column assumed. A `ULong` keeps the bits of a `Long`, as no 64-bit signed integer holds the values above `Long.MAX_VALUE`. Reading is unchanged and reads the old rows right, but in SQL an old value above the signed range stays negative until it is written again, which one UPDATE per column does: `UPDATE t SET c = c + 256 WHERE c < 0` for a `UByte` column, with `65536` for a `UShort` one and `4294967296` for a `UInt` one

### sqllin-driver

* Fix: the JVM driver bound a `UInt`, `UShort` or `UByte` parameter as the bits of a signed number, unlike the Kotlin/Native driver, and the Android driver bound one written through `execSQL` as its string. All of them now bind it as its number, and a `ULong` as the `Long` of the same bits

### sqllin-processor

* Fix: the generated `createSQL` of a table didn't escape `"`, `\` and `$`, which a Kotlin string literal treats specially, so a `@Default` value holding one could make the generated code fail to compile, or hold another value
* **Breaking change**: Fix: reading a column's property in `SET {}`, as in `SET { visits = visits + 1 }`, compiled, but its getter only returns a placeholder, such as `0` or `""`, so the column was set to a value computed from it, here `1`. The getter is now deprecated with the level `ERROR`, so reading the property is a compile error, while assigning it is unchanged

## 2.4.0 / 2026-10-03

### All

* Update `Kotlin`'s version to `2.4.20`
* Update `AGP`'s version to `9.4.1`
* Migrate the Android instrumented tests to `Robolectric`, they now run on the JVM as host unit tests against `API 26` and `API 37`, and no longer need an emulator
* Move the `sqllin-driver` tests back into the `sqllin-driver` module's `commonTest`, and remove the `sqllin-driver-test` module

### sqllin-dsl

* New DSL API: `DatabaseScope#INSERT_OR_IGNORE` for SQL syntax `INSERT OR IGNORE`
* New DSL API: projection, which reads `SELECT` results into a narrower `@Serializable` type naming the columns to select, given as the type argument of the clause function: `X<R>()`, `WHERE<R>(...)`, `ORDER_BY<R>(...)`, `LIMIT<R>(...)` and `GROUP_BY<R>(...)`, after `SELECT` or `SELECT_DISTINCT`. A type that doesn't fit the table is rejected with an `IllegalArgumentException` when the statement is built. To support it, the public `DatabaseScope#select` functions now take a result type separate from the table's; this is source-compatible, but on Kotlin/Native a library compiled against an earlier version may have to be recompiled
* New DSL API: result columns, which select expressions such as aggregate functions into properties of a result type with `AS`, as in `table SELECT listOf(count(X) AS AuthorStats::books, sum(pages) AS AuthorStats::totalPages) GROUP_BY author`, or `table SELECT (count(X) AS BookCount::books)` for a single one. The result type's other properties are read from their columns, as in a projection. They work after `SELECT` and `SELECT_DISTINCT`, followed by `WHERE`, `GROUP_BY`, `ORDER_BY` and `LIMIT`. A property must have the type of its expression's values, which is checked at compile time, and be nullable when its expression can be `NULL`, which is checked when the statement is built. An aggregate query without `GROUP BY` returns a row even when no rows match, in which every column and every aggregate function except `count` is `NULL`; as `GROUP_BY` can still follow when the statement is built, this is checked when the scope ends, before any of its statements runs
* New DSL API: `INSERT`, `INSERT_OR_IGNORE` and `INSERT_OR_REPLACE` taking a `SelectStatement` of the table's row type, for SQL syntax `INSERT INTO ... SELECT`, as in `ArchiveTable INSERT (PersonTable SELECT WHERE<Archive>(PersonTable.age GT 60))`. Every column is inserted, the primary key copied as it is selected. The `SELECT` becomes part of the `INSERT`, so it no longer runs on its own
* New API: `Table#withName`, which returns a table with the same structure under another name. With `INSERT INTO ... SELECT`, it rebuilds a table in a migration, for a change `ALTER TABLE` can't make, such as adding a constraint, or dropping a column on SQLite older than 3.35, which on Android means below API 34. The guide to modifying the database now describes the procedure, and documents `INSERT_OR_IGNORE` and `INSERT_OR_REPLACE`
* **Breaking change**: `ClauseElement`, `ClauseNumber` and `ClauseString` now take a type parameter, the type of their values, such as `ClauseNumber<Int>` for an `Int` column and `ClauseNumber<Long>` for `count(X)`, which is what lets `AS` check the type of a property. Code that only uses the DSL is unaffected; code that names these types has to add a type argument, such as `ClauseElement<*>` where any element is accepted. The public constructors of `ClauseNumber`, `ClauseString`, `ClauseBoolean`, `ClauseBlob` and `ClauseEnum`, which the generated table objects call, now take whether the column is nullable instead of whether the element is a function. The generated code is regenerated by the build, but on Kotlin/Native a library compiled against an earlier version has to be recompiled
* **Breaking change**: The SQL functions now return elements of the type of the values SQLite returns for them: `count`, `length`, `instr` and `random` a `ClauseNumber<Long>`, `avg` and `round` a `ClauseNumber<Double>`, the string functions and `group_concat` a `ClauseString<String>`, and `max`, `min` and `abs` an element of the same kind and type as their argument. So `max` and `min` of a String column are now a `ClauseString`, compared with strings in `HAVING`, where they used to be a `ClauseNumber`. `sum` is overloaded by the type of its column: of a column of integers or Booleans it is a `ClauseNumber<Long>`, and of a `Float` or `Double` column a `ClauseNumber<Double>`. A `sum` of a String, BLOB, enum or `ULong` column no longer compiles; the last because SQLite stores a `ULong` above `Long.MAX_VALUE` as a negative number, which made the sum wrong
* Fix documentation: the SQL functions guide listed a `sign` function, which isn't available, and its `HAVING (count(X) > 2)` example didn't compile; it is `HAVING (count(X) GT 2)`. It no longer says that functions can only be used in conditions
* **Breaking change**: The parameter of annotation `@PrimaryKey` renamed from `isAutoincrement` to `autoIncrement`, aligning it with the name already used in the documentation and with the naming of the other annotations. Call sites using the named argument `@PrimaryKey(isAutoincrement = true)` must be updated to `@PrimaryKey(autoIncrement = true)`; positional usage such as `@PrimaryKey(true)` is unaffected
* **Breaking change**: The nullability of a `@PrimaryKey` property now decides who supplies its value. A `Long?` key is assigned by the database, as before. A non-null `Long` key is now allowed: it remains an `INTEGER PRIMARY KEY`, a rowid alias, but is supplied by the caller and written by every `INSERT`. A key of any other type must be non-null and is declared `NOT NULL`. Previously every `@PrimaryKey` was forced to be nullable, against the annotation's own documentation, and because SQLite does not let `PRIMARY KEY` imply `NOT NULL` on such a column, a `String` key could hold `NULL` in any number of rows. `autoIncrement = true` now requires a `Long?` key, and a `ULong?` key, which used to be stored as `NULL` because it was left out of `INSERT` without being a rowid alias, is now rejected. To migrate, drop the `?` from any non-`Long` `@PrimaryKey`; a single-column `@CompositePrimaryKey` that only existed to hold a caller-supplied `Long` key can become `@PrimaryKey val id: Long`
* **Breaking change**: `@CompositePrimaryKey` now requires at least two properties. A single-column primary key is declared with `@PrimaryKey`, which for a `Long` key maps to `INTEGER`, a rowid alias, where a single-column `@CompositePrimaryKey` mapped it to `BIGINT`. To migrate, replace a lone `@CompositePrimaryKey` with `@PrimaryKey`
* **Breaking change**: The DSL APIs `ALERT_ADD_COLUMN` and `ALERT_RENAME_TABLE_TO` renamed to `ALTER_ADD_COLUMN` and `ALTER_RENAME_TABLE_TO`, correcting a misspelling of the SQL keyword `ALTER`. The internal `Alert` operation object is renamed to `Alter` accordingly
* Fix: the ALTER operations emitted the invalid keyword `ALERT TABLE` instead of `ALTER TABLE`, so `ALTER_ADD_COLUMN`, `ALTER_RENAME_TABLE_TO`, `RENAME_COLUMN` and `DROP_COLUMN` all failed at runtime and had never worked. The tests that covered them swallowed the failure, which is why it went unnoticed
* Fix documentation: the KDoc of `DatabaseScope` now states that statement execution is deferred until the scope exits, and its example no longer reads a `SelectStatement`'s results while the scope is still open, which throws
* Fix documentation: the `CREATE_INDEX` and `CREATE_UNIQUE_INDEX` examples referenced a `KClass.table` extension that does not exist in the library, and referred to columns by property reference instead of through the generated table object
* Fix: the SQL string functions added in 2.2.0, `substr`, `trim`, `ltrim`, `rtrim`, `replace`, `instr` and `printf`, now carry the same DSL marker as the other SQL functions, so IntelliJ IDEA highlights their calls the same way
* Fix documentation: the installation guide now declares the task dependencies the generated code needs, as SQLlin's own builds always did. Without them Gradle fails the build, in particular when another KSP processor runs in the same module. It also states that each generated object is named after its class with a `Table` suffix, not after the table
* Fix: the string arguments of `replace`, `instr`, `printf` and `group_concat` were put into the SQL between single quotes without escaping, so a `'` in one broke the statement, and a crafted one could change what the statement does, such as making a condition true for every row. A `'` is now escaped as `''`, the only escape SQLite has in a string literal, so any string stays a literal
* Fix: a comparison between two elements, such as `length(name) GT pages` or `HAVING (max(pages) GT min(pages))`, qualified both with their table's name even when one was a function, producing invalid SQL such as `book.length(name)`. A function is now written as it is

### sqllin-driver

* Update `sqlite-jdbc`'s version to `3.53.4.0`

### sqllin-processor

* Update `KSP`'s version to `2.3.12`
* Fix: the visibility of the class annotated with `@DBRow` is now propagated to the generated table object. An `internal` `@DBRow` class used to produce a `public` object, which failed to compile with `EXPOSED_SUPER_CLASS`, `EXPOSED_FUNCTION_RETURN_TYPE` and `EXPOSED_RECEIVER_TYPE`. A `@DBRow` class that is neither `public` nor `internal` is now reported as an error
* Fix: every `SetClause` property generated for a column declared after a nullable `Long` `@PrimaryKey` was typed nullable regardless of the column's own declaration, so `UPDATE ... SET { column = null }` compiled against `NOT NULL` columns and failed only at runtime. Each property now takes the nullability its own column declares
* Fix: the columns of a `@CompositePrimaryKey` are now declared `NOT NULL`. SQLite, unlike standard SQL, does not let a table-level `PRIMARY KEY` imply it on a rowid table, so such a key used to accept `NULL`, and any number of rows sharing the same key once a `NULL` was part of it. SQLlin itself could not write those `NULL`s, but anything else writing to the database could, and SQLlin then read them back as `0` or an empty string. This only changes the schema of tables created from now on; an existing table keeps its schema, as SQLite cannot add `NOT NULL` to an existing column
* **Breaking change**: an `ON_DELETE_SET_DEFAULT` or `ON_UPDATE_SET_DEFAULT` foreign key now requires the column to declare `@Default`, as the documentation always said. On `@References` the check was inverted: it accepted a non-null column without a default, whose parent row then could not be deleted (`NOT NULL constraint failed`), and rejected a nullable one. On `@ForeignKey` groups there was no check at all, so a nullable column without a default compiled and was set to `NULL`; that is now rejected too. To migrate, add `@Default` to the column, or use `ON_DELETE_SET_NULL` / `ON_UPDATE_SET_NULL` where setting it to `NULL` is the intent. Both checks hold whichever order `@Default` and the foreign key annotation are written in
* Fix: a computed property of a `@DBRow` class, one without a backing field such as `val title: String get() = ...`, no longer becomes a column. kotlinx.serialization doesn't serialize such a property, but it was given a `NOT NULL` column that `INSERT` never wrote, so every insert failed with `NOT NULL constraint failed`, and an accessor that looked its column up past the end of the serializer's descriptor
* Fix: a `@DBRow` property of a type no column can hold, such as a `List`, is now a compile-time error naming the property. It used to be skipped silently: left out of `CREATE TABLE` while its serializer still wrote and read it, so `INSERT` and `SELECT` failed at runtime with "no column named", and as the last property it left a trailing comma that made `CREATE TABLE` itself fail. Annotate such a property with `kotlinx.serialization.Transient` to keep it out of the table
* Fix: the generated table objects no longer produce a `DSL_MARKER_APPLIED_TO_WRONG_TARGET` warning for every column, which Kotlin 2.3.20 and later report in the module that compiles them. Their `@ColumnNameDslMaker` is there for IntelliJ IDEA's DSL highlighting rather than for the compiler's DSL scope control, so the warning is now suppressed on each generated object
* Fix: the generated `SetClause` setter of a non-null enum column no longer uses a safe call, `value?.ordinal`, which the module compiling the generated code reported as unnecessary. Only a nullable enum column keeps it

## 2.3.0 / 2026-08-20

### All

* Update `Kotlin`'s version to `2.4.10`
* Update `AGP`'s version to `9.3.1`, migrated from `com.android.library` plugin to `com.android.kotlin.multiplatform.library`
* Update `kotlinx.serialization`'s version to `1.11.0`
* Update `kotlinx.coroutines`'s version to `1.11.0`
* Fix documentation: Android minimum supported version has been `7.0+` (API 24) since `2.0.0`, the README incorrectly stated `6.0+`
* **Breaking change**: Drop `iosX64`, `macosX64`, `watchosX64`, and `tvosX64` target support

### sqllin-dsl

* New DSL API: `DatabaseScope#INSERT_OR_REPLACE` for SQL syntax `INSERT OR REPLACE`

### sqllin-driver

* Update `sqlite-jdbc`'s version to `3.53.2.1`

### sqllin-processor

* Update `KSP`'s version to `2.3.11`

## 2.2.0 / 2025-12-15

### sqllin-dsl

* New experimental DSL API: `DatabaseScope#CREATE_INDEX` for creating indexes
* New experimental DSL API: `DatabaseScope#CREATE_UNIQUE_INDEX` for creating unique indexes
* New experimental DSL API: `DatabaseScope#PRAGMA_FOREIGN_KEYS` for enabling foreign keys
* New experimental annotation APIs: `ForeignKeyGroup`, `References`, `ForeignKey` for supporting foreign keys for table and column levels
* New experimental annotation API: `@Default` for specifying default values for columns in CREATE TABLE statements
* New SQL aggregate function: `group_concat` for concatenating values with a separator
* New SQL scalar functions: `round`, `random`
* New SQL string functions: `substr`, `trim`, `ltrim`, `rtrim`, `replace`, `instr`, `printf`
* New overload for `length` function to support `ClauseBlob` type
* **Breaking change**: The parameter type of `abs` function changed from `ClauseElement` to `ClauseNumber`
* **Breaking change**: The parameter type of `upper` function changed from `ClauseElement` to `ClauseString`
* **Breaking change**: The parameter type of `lower` function changed from `ClauseElement` to `ClauseString`
* **Breaking change**: The parameter type of `length` function changed from `ClauseElement` to `ClauseString`

### sqllin-driver

* Update the `sqlite-jdbc`'s version to `3.51.1.0`

### sqllin-processor

* Update `KSP`'s version to `2.3.3`

## 2.1.0 / 2025-11-04

### sqllin-dsl

* Support typealias of supported types(primitive types, String, ByteArray etc) in generated tables
* Support enumerated types in DSL APIs, includes `=`, `!=`, `<`, `<=`, `>`, `>=` operators
* Support `<`, `<=`, `>`, `>=`, `IN`, `BETWEEN...AND` operators for String
* Support `=`, `!=`, `<`, `<=`, `>`, `>=`, `IN`, `BETWEEN...AND` operators for ByteArray
* Add a new condiction function `ISNOT` for Boolean, and `IS` starts to support to receive a nullable parameter
* Refactored _CREATE_ statements building process, move it from runtime to compile-time.
* New experimental annotation API for _COLLATE NOCASE_ keyword: `@CollateNoCase`
* New experimental annotation API for single column with _UNIQUE_ keyword: `@Unique`
* New experimental annotation API for composite column groups with _UNIQUE_ keyword: `@CompositeUnique`

## 2.0.0 / 2025-10-23

### All

* Update `Kotlin`'s version to `2.2.21`
* Remove the Desuger configuration
* Update minimal supported Android version from API 23 to 24

### sqllin-dsl

* Optimized performance for SQL assembly
* New experimental annotation for marking primary key: `@PrimaryKey`
* New experimental annotation for marking composite primary key: `@CompositePrimaryKey`
* New experimental API for creating Database: `DSLDBConfiguration`
* New experimental DSL API: `DatabaseScope#CREATE`
* New experimental DSL API: `DatabaseScope#DROP`
* New experimental DSL API: `DatabaseSceop#ALERT`
* Support using `ByteArray` in DSL, that represents _BLOB_ in SQLite

### sqllin-driver

* Update the `sqlite-jdbc`'s version to `3.50.3.0`
* **Breaking change**: The data type of `bindParams` in `DatabaseConnection#query` changed from `Array<out String?>?` to `Array<out Any?>?`

### sqllin-processor

* Update `KSP`'s version to `2.3.0`

## 1.4.4 / 2025-07-07

### All

* Update `Kotlin`'s version to `2.2.0`

### sqllin-dsl

* Update `kotlinx.serialization`'s version to `1.9.0`

### sqllin-driver

* Update the `sqlite-jdbc`'s version to `3.50.2.0`

### sqllin-processor

* Update `KSP`'s version to `2.2.0-2.0.2`

## v1.4.3 / 2025-06-02

### All

* Update `Kotlin`'s version to `2.1.21`

### sqllin-processor

* Update `KSP`'s version to `2.1.21-2.0.1`

## v1.4.2 / 2025-04-23

### All

* Update `Kotlin`'s version to `2.1.20`

### sqllin-dsl

* Update `kotlinx.coroutines`'s version to `1.10.2`
* Update `kotlinx.serialization`'s version to `1.8.1`

### sqllin-driver

* Update the `sqlite-jdbc`'s version to `3.49.1.0`

### sqllin-processor

* Update `KSP`'s version to `2.1.20-1.0.32`

## v1.4.1 / 2025-02-04

### All

* Update `Kotlin`'s version to `2.1.10`

### sqllin-dsl

* Update `kotlinx.coroutines`'s version to `1.10.1`
* Update `kotlinx.serialization`'s version to `1.8.0`
* Add some `DslMaker` annotations, make the DSL apis be more readable

### sqllin-driver

* Update the `sqlite-jdbc`'s version to `3.48.0.0`

### sqllin-processor

* Update `KSP`'s version to `2.1.10-1.0.29`

## v1.4.0 / 2024-12-04

### All

* Update `Kotlin`'s version to `2.1.0`

### sqllin-dsl

* Update `kotlinx.coroutines`'s version to `1.9.0`
* Update `kotlinx.serialization`'s version to `1.7.3`

### sqllin-driver

* Update the `sqlite-jdbc`'s version to `3.47.1.0`

### sqllin-processor

* Update `KSP`'s version to `2.1.0-1.0.29`

## v1.3.2 / 2024-06-18

### All

* Update `Kotlin`'s version to `1.9.24`

### sqllin-dsl

* Now, you can annotate properties with `kotlinx.serialization.transmint` in your data classes to ignore these properties when serialization or deserialization and `Table` classes generation.

### sqllin-processor

* Update `KSP`'s version to `1.9.24-1.0.20`

## v1.3.1 / 2024-04-24

### sqllin-dsl

* Fix a crash when a data class doesn't contain any `String` element.
* Fix the [issue#81](https://github.com/ctripcorp/SQLlin/issues/81) about insert and query null values
* Fix some wrongs about generation of SQL syntax

### sqllin-driver

* **Breaking change**: Remove the deprecated API `CommonCursor#forEachRows`
* **Breaking change**: the `getInt`, `getLong`, `getFloat` and `getDouble` will throw an exception when the value is NULL in SQLite
* Add a new public API: `CommonCursor#isNull`, for check if the value is NULL in SQLite

## v1.3.0 / 2024-04-21

### All

* Update `Kotlin`'s version to `1.9.23`

### sqllin-dsl

* Update `kotlinx.coroutines`'s version to `1.8.0`
* Update `kotlinx.serialization`'s version to `1.6.3`
* Modify the SQL statements' splicing method, that fixed the [issue#77](https://github.com/ctripcorp/SQLlin/issues/77) that users can't read/write special symbols as the values in SQL statements.
* Performance optimization, use `ArrayDeque` to replace the LinkedList for SQL statements management (self-implemented).
* The parameter `enableSimpleSQLLog` of the `Database`'s constructors of is `false` by default.

### sqllin-driver

* Update the `sqlite-jdbc`'s version to `3.45.3.0`

### sqllin-processor

* Update `KSP`'s version to `1.9.23-1.0.20`

## v1.2.4 / 2024-01-05

### All

* Update `Kotlin`'s version to `1.9.22`

### sqllin-dsl

* Update `kotlinx.serialization`'s version to `1.6.2`

### sqllin-processor

* Update `KSP`'s version to `1.9.22-1.0.16`

## v1.2.3 / 2023-11-28

### All

* Update `Kotlin`'s version to `1.9.21`

### sqllin-dsl

* Now, the `ORDER_BY` clause could ignore the `OrderByWay` parameter like SQL.
* Optimize the performance in concurrent scenarios. Some types have changed, but users don't need to change the code.
* Now, `SelectStatement` has been changed to lazy deserialization mode, that's means the first time you invoke the 
function `SelectStatement#getResults` will consume more time. But, correspondingly, executing `SELECT` statements will be faster.
* Add the `enableSimpleSQLLog` parameter to `Database`'s constructor, default by `true`, if you set it to
`false`, you can disable the simple SQL logout.

### sqllin-driver

* Deprecated the public API `CommonCursor#forEachRows`, you can replace with `CommonCursor#forEachRow`. This
change just for fixing a typo :). And, the `CommonCursor#forEachRows` will be removed in next version.

### sqllin-processor

* Update `KSP`'s version to `1.9.21-1.0.15`

## v1.2.2 / 2023-11-08

### All

* Update `Kotlin`'s version to `1.9.20`

### sqllin-dsl

* Add the new native target support: `linuxArm64`
* Add the new API `Database#suspendedScope`, it could be used to ensure concurrency safety([#55](https://github.com/ctripcorp/SQLlin/pull/55))
* Begin with this version, _sqllin-dsl_ depends on _kotlinx.coroutines_ version `1.7.3`
* **Breaking change**: Remove the public class `DBEntity`, we have deprecated it in version `1.1.1`

### sqllin-driver

* Add the new native target support: `linuxArm64`

### sqllin-processor

* Update `KSP`'s version to `1.9.20-1.0.13`
* Fix the bug for when the code that is generated by `sqllin-processor` can't be compiled([#58](https://github.com/ctripcorp/SQLlin/pull/58))

## v1.2.1 / 2023-10-18

### All

* Update `Kotlin`'s version to `1.9.10`

### sqllin-driver

* Fix the problem: [Native driver does not respect isReadOnly](https://github.com/ctripcorp/SQLlin/issues/50). ***On native platforms***. 
Now, if a user set `isReadOnly = true` in `DatabaseConfigurtaion`, the database file must exist. And, if opening in read-write mode 
fails due to OS-level permissions, the user will get a read-only database, and if the user try to modify the database, will receive
a runtime exception. Thanks for [@nbransby](https://github.com/nbransby).

### sqllin-processor

* Update `KSP`'s version to `1.9.10-1.0.13`
* Now, if your data class with `@DBRow` can't be solved or imported successfully(Using `KSNode#validate` to judge), the
`ClauseProcessor` would try to resolve it in second round.

## v1.2.0 / 2023-09-19

### sqllin-dsl

* Add the new JVM target

### sqllin-driver

* Add the new JVM target
* **Breaking change**: Remove the public property: `DatabaseConnection#closed`
* The Android(< 9) target supports to set the `journalMode` and `synchronousMode` now

## v1.1.1 / 2023-08-12

### All

* Update `Kotlin`'s version to `1.9.0`

### sqllin-dsl

* Deprecated the public API `DBEntity`([#36](https://github.com/ctripcorp/SQLlin/pull/36), [#37](https://github.com/ctripcorp/SQLlin/pull/37)), any data classes used in _sqllin-dsl_ don't need to extend `DBEntity` anymore.

### sqllin-driver

* Fix a bug about empty `ByteArray` on native platforms([#30](https://github.com/ctripcorp/SQLlin/pull/30))

### sqllin-processor

* Update `KSP`'s version to `1.9.0-1.0.13`

## v1.1.0 / 2023-06-06

### All

* Remove the `iosArm32`, `watchosX86` and `mingwX86` these three targets' support
* Add the new native target support: `watchosDeviceArm64`

### sqllin-dsl

* Update `kotlinx.serialization`'s version to `1.5.1`

### sqllin-driver

* Enable the `New Native Driver` to replace [SQLiter](https://github.com/touchlab/SQLiter)
* Make some unnecessary APIs be internal (`CursorImpl`, `DatabaseConnectionImpl` and more...)
* Add the new public function in `Cursor#next`
* Add the new public function `deleteDatabase`
* Add the new public property: `DatabaseConnection#isClosed`
* Deprecated the public property: `DatabaseConnection#closed`

## v1.0.1 / 2023-05-14

### All

* Update `Kotlin`'s version to `1.8.20`

### sqllin-dsl

* Update `kotlinx.serialization`'s version to `1.5.0`

### sqllin-processor

* Update `KSP`'s version to `1.8.20-1.0.11`

## v1.0.0 / 2022-12-29

### All

* Fix some bugs about unit tests

### sqllin-dsl

* Add the `ON` clause support
* Fix some bugs about `JOIN` clause

### sqllin-processor

* Update `KSP`'s version to `1.7.20-1.0.8`

## v1.0-alpha01 / 2022-11-29

### Initial Release

* Based on `Kotlin 1.7.20`
* Based on `KSP 1.7.20-1.0.7`
* Based on `kotlinx.serialization 1.4.1`