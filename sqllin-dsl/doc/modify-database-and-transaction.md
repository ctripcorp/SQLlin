# Modify Database and Transaction

中文版请见[这里](modify-database-and-transaction-cn.md)

In [Getting Start](getting-start.md), we have learned how to create the `Database` instance and define your database entities. Now,
we start to learn how to write SQL statements with SQLlin.

## Table Structure Operations

SQLlin provides type-safe DSL operations for managing table structures: CREATE, DROP, and ALTER, and for views, indexes and triggers.

### CREATE - Creating Tables

You can create tables directly from your data class definitions using the CREATE operation:

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class Person(
    @PrimaryKey(autoIncrement = true)
    val id: Long = 0,
    val name: String,
    val age: Int,
)

fun sample() {
    database {
        // Create table using infix notation
        CREATE(PersonTable)

        // Or using extension function
        PersonTable.CREATE()
    }
}
```

The CREATE operation automatically generates the appropriate SQL CREATE TABLE statement based on your data class definition, including:
- Correct column types (String → TEXT, Int → INT, Long → INTEGER/BIGINT, etc.)
- NOT NULL constraints for non-nullable properties
- PRIMARY KEY constraints (single or composite)
- AUTOINCREMENT for auto-incrementing primary keys

### DROP - Removing Tables

The DROP operation permanently removes a table and all its data from the database:

```kotlin
fun sample() {
    database {
        // Drop table using infix notation
        DROP(PersonTable)

        // Or using extension function
        PersonTable.DROP()
    }
}
```

**⚠️ WARNING**: DROP is a destructive operation. Once executed, the table and all its data are permanently deleted. Use with caution.

### CREATE VIEW - Creating Views

A view is a stored _SELECT_ that queries read like a table. Declare its rows with `@DBView`, as you declare a table's
with `@DBRow`, and _sqllin-processor_ generates a view object named after the class with a `View` suffix. Views are
experimental, so opt in with `@OptIn(ExperimentalDSLDatabaseAPI::class)`:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBView("adults")
@Serializable
data class Adult(
    val name: String,
    val age: Int,
)

@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        // CREATE VIEW adults(name,age) AS SELECT name,age FROM person WHERE person.age>=18
        CREATE_VIEW(AdultView) AS (PersonTable SELECT WHERE<Adult>(PersonTable.age GTE 18))
    }
    lateinit var olderAdults: SelectStatement<Adult>
    database {
        AdultView { view ->
            olderAdults = view SELECT WHERE(age GT 60)
        }
    }
}
```

The _SELECT_ after `AS` has to read rows of the view's type, which is checked at compile time. It can be any _SELECT_:
with a projection, result columns, a join or a compound query. SQLite doesn't let a view take parameters, so SQLlin
writes the values of the _SELECT_ into the view's SQL.

A view is queried like a table, with the properties of its view object as columns, in joins and compound queries too,
and `Database#observe` watches the tables it reads. It can't be written to: its object isn't a `Table`, so `INSERT`,
`UPDATE`, `DELETE`, `CREATE` and `ALTER` don't compile on it, and its properties take no constraint annotations.
`DROP(AdultView)` drops it. A view's definition is part of the schema: to change it, drop it and create it again in an
`upgrade`.

### CREATE INDEX - Creating Indexes

An index makes the queries that look rows up by its columns fast. `CREATE_INDEX` creates one on columns of a table, and
`CREATE_UNIQUE_INDEX` one that also keeps any two rows from having the same values in them:

```kotlin
fun sample() {
    database {
        // CREATE INDEX idx_person_name ON person(name)
        PersonTable.CREATE_INDEX("idx_person_name", PersonTable.name)
        // CREATE UNIQUE INDEX idx_person_name_age ON person(name,age)
        PersonTable.CREATE_UNIQUE_INDEX("idx_person_name_age", PersonTable.name, PersonTable.age)
        // CREATE INDEX idx_person_lower_name ON person(lower(name))
        PersonTable.CREATE_INDEX("idx_person_lower_name", PersonTable.lower(PersonTable.name))
    }
}
```

A function of the table's columns makes an expression index, which the queries that compare the same expression use, as
`lower(name)` does for case-insensitive lookups. An index can only hold the columns of its own table.

Each column of an index can be given the order it is sorted in, as `ORDER_BY` gives it:
`PersonTable.CREATE_INDEX("idx_person_age", PersonTable.age to DESC)` is `CREATE INDEX idx_person_age ON person(age DESC)`.
This is experimental. A query that sorts the columns in that order, or the reverse, reads the index in order.

`WHERE` after `CREATE_INDEX` or `CREATE_UNIQUE_INDEX` makes a partial index: an index of the rows that satisfy a condition
only. It is smaller than an index of all rows, and a unique one keeps the values unique among those rows only, such as
the emails of the users that aren't deleted. Partial indexes are experimental:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        // CREATE UNIQUE INDEX idx_user_email ON user(email) WHERE isDeleted<=0
        UserTable.CREATE_UNIQUE_INDEX("idx_user_email", UserTable.email) WHERE (UserTable.isDeleted IS false)
    }
}
```

SQLite allows neither parameters nor subqueries in the condition of an index, so SQLlin writes the values of the
condition into the SQL, and a condition with a subquery throws an `IllegalArgumentException`. A query uses a partial
index only when its _WHERE_ implies the condition of the index.

`DROP_INDEX`, which is experimental, drops an index by its name:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        DROP_INDEX("idx_person_name")
    }
}
```

### IF NOT EXISTS and IF EXISTS

`CREATE` fails if the table already exists, and `DROP` if it doesn't. Their `IF NOT EXISTS` and `IF EXISTS` versions do
nothing instead, for tables, views and indexes. They are experimental:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        // CREATE TABLE IF NOT EXISTS person(...)
        CREATE_IF_NOT_EXISTS(PersonTable)  // Or PersonTable.CREATE_IF_NOT_EXISTS()
        PersonTable.CREATE_INDEX_IF_NOT_EXISTS("idx_person_name", PersonTable.name)
        PersonTable.CREATE_UNIQUE_INDEX_IF_NOT_EXISTS("idx_person_name_age", PersonTable.name, PersonTable.age)
        CREATE_VIEW_IF_NOT_EXISTS(AdultView) AS (PersonTable SELECT WHERE<Adult>(PersonTable.age GTE 18))

        DROP_IF_EXISTS(AdultView)  // Or AdultView.DROP_IF_EXISTS()
        DROP_INDEX_IF_EXISTS("idx_person_name")
        DROP_IF_EXISTS(PersonTable)  // Or PersonTable.DROP_IF_EXISTS()
    }
}
```

`IF NOT EXISTS` only checks the name: if a table of that name exists with other columns, it stays as it is.

### CREATE TRIGGER - Creating Triggers

A trigger runs statements whenever a row of a table is inserted, deleted or updated, before or after, where a condition
holds. Triggers are experimental:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        // CREATE TRIGGER person_deleted AFTER DELETE ON person
        // BEGIN INSERT INTO audit(personId,action,at) VALUES (OLD.id,'delete',datetime('now')); END
        CREATE_TRIGGER("person_deleted") AFTER DELETE ON PersonTable BEGIN {
            AuditTable { table ->
                table INSERT listOf(
                    old(PersonTable.id) AS Audit::personId,
                    literal("delete") AS Audit::action,
                    datetime("now") AS Audit::at,
                )
            }
        }
        // CREATE TRIGGER no_negative_age BEFORE INSERT ON person WHEN NEW.age<0
        // BEGIN SELECT RAISE(ABORT,'age can''t be negative'); END
        CREATE_TRIGGER("no_negative_age") BEFORE INSERT ON PersonTable WHEN (new(PersonTable.age) LT 0) BEGIN {
            RAISE(ABORT, "age can't be negative")
        }
    }
}
```

- `BEFORE` or `AFTER` gives the time, and `INSERT`, `DELETE`, `UPDATE` or `UPDATE_OF(columns)` the event. `WHEN` can give
  a condition.
- `new(column)` and `old(column)` read the row being written: a trigger on INSERT has a new row, one on DELETE an old
  row, and one on UPDATE both. Reading a row the trigger doesn't have is rejected when the statement is built, as
  SQLite would only fail when the trigger fires.
- The body inserts, updates and deletes rows, with the [expressions](sql-functions.md#expressions) of `SET` and
  `INSERT`. `RAISE(ABORT, message)` stops the statement that fired the trigger with that message, as `FAIL` and
  `ROLLBACK` do in their ways, and `RAISE(IGNORE)` skips its row.
- The statements run when the trigger fires, not when the scope ends. SQLite doesn't let a trigger take parameters, so
  the values in its SQL are literals.

`CREATE_TRIGGER_IF_NOT_EXISTS` skips a trigger that exists, and `DROP_TRIGGER(name)` and `DROP_TRIGGER_IF_EXISTS(name)`
drop one. A trigger can't be on an FTS table, which is a virtual table. Dropping a table drops its triggers.

The rows a trigger writes count as changes for [observed queries](advanced-query.md#observed-queries), FTS tables
included.

### ALTER - Modifying Table Structure

SQLlin provides several ALTER operations for modifying existing table structures:

#### Add Column

Add a new column to an existing table:

```kotlin
@DBRow
@Serializable
data class Person(
    val name: String,
    val age: Int,
    val email: String? = null,  // New column
)

fun sample() {
    database {
        PersonTable ALTER_ADD_COLUMN PersonTable.email
    }
}
```

#### Rename Table

Rename an existing table to a new name:

```kotlin
fun sample() {
    database {
        // Rename using Table object
        PersonTable ALTER_RENAME_TABLE_TO NewPersonTable

        // Or rename using old table name as String
        "old_person" ALTER_RENAME_TABLE_TO NewPersonTable
    }
}
```

#### APIs That Depend on the SQLite Version

Some APIs only work with a SQLite version, or a compile-time option of SQLite, that some platforms don't have. The
SQLite SQLlin runs on depends on the platform: Android's system SQLite is 3.9 at API 24, SQLlin's minimum, 3.32 below
API 34 and 3.39 from API 34 on, and lacks options such as the math functions; Apple's system SQLite depends on the OS
version; and on Linux and Windows, it is the SQLite your app links. Such an API fails at runtime where the SQLite lacks
what it needs, so it is marked with `@PlatformDependentSQLiteAPI`, which is a compile error unless you opt in with
`@OptIn(PlatformDependentSQLiteAPI::class)`. Opt in only when all your app's platforms have what the API needs, which
its documentation describes.

#### Rename Column

Rename a column within a table. `ALTER TABLE ... RENAME COLUMN` needs SQLite 3.25.0, which Android has from API 30 on:

```kotlin
@OptIn(PlatformDependentSQLiteAPI::class)
fun sample() {
    database {
        // Using ClauseElement references (type-safe)
        PersonTable.RENAME_COLUMN(PersonTable.age, PersonTable.yearsOld)

        // Or using String for old column name
        PersonTable.RENAME_COLUMN("age", PersonTable.yearsOld)
    }
}
```

#### Drop Column

Remove a column from an existing table. `ALTER TABLE ... DROP COLUMN` needs SQLite 3.35.0:

```kotlin
@OptIn(PlatformDependentSQLiteAPI::class)
fun sample() {
    database {
        PersonTable DROP_COLUMN PersonTable.email
    }
}
```

**⚠️ WARNING**: DROP COLUMN permanently deletes the column and all its data. Note that SQLite's DROP COLUMN support was added in version 3.35.0, which Android only has from API 34 on, so older SQLite versions require [rebuilding the table](#rebuilding-a-table).

### Using Structure Operations with DSLDBConfiguration

These operations are particularly useful in database creation and upgrade callbacks when using `DSLDBConfiguration`:

```kotlin
import com.ctrip.sqllin.dsl.DSLDBConfiguration

val database = Database(
    DSLDBConfiguration(
        name = "Person.db",
        path = getGlobalDatabasePath(),
        version = 2,
        create = {
            CREATE(PersonTable)
            CREATE(AddressTable)
        },
        upgrade = { oldVersion, newVersion ->
            when (oldVersion) {
                1 -> {
                    // Upgrade from version 1 to 2
                    PersonTable ALTER_ADD_COLUMN PersonTable.email
                    CREATE(AddressTable)
                }
            }
        }
    )
)
```

### Rebuilding a Table

SQLite's `ALTER TABLE` can only rename a table, and add, rename or drop a column. Any other change, such as adding a
constraint, making a column `NOT NULL` or changing the primary key, needs the table to be rebuilt: create the new
structure under a temporary name, copy the rows into it with `INSERT INTO ... SELECT`, drop the old table, and rename
the new one. That is also how to drop a column where SQLite is older than 3.35, which on Android means below API 34.

To select from the old table, keep a `@DBRow` class with its old structure, under the same table name. `withName`
gives the new structure a temporary name:

```kotlin
import com.ctrip.sqllin.dsl.sql.withName

@DBRow("person")
@Serializable
data class PersonV1(        // the structure of 'person' in version 1
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val name: String,
    val legacy: Int,
)

@DBRow("person")
@Serializable
data class Person(          // the structure from version 2 on
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @Unique val fullName: String,
)

val database = Database(
    DSLDBConfiguration(
        // ...
        version = 2,
        upgrade = { oldVersion, newVersion ->
            if (oldVersion < 2) {
                val newPerson = PersonTable.withName("person_new")
                CREATE(newPerson)
                PersonV1Table { table ->
                    // INSERT INTO person_new(id,fullName) SELECT id,person.name AS fullName FROM person
                    newPerson INSERT (table SELECT (name AS Person::fullName))
                }
                DROP(PersonV1Table)
                "person_new" ALTER_RENAME_TABLE_TO PersonTable
            }
        }
    )
)
```

Rename the new table, not the old one. Where SQLite's `legacy_alter_table` setting is off, the default on the JVM, Linux
and Windows, renaming a table also renames the references to it in the foreign keys of other tables, so the references
would follow the old table, and be left pointing at a table that no longer exists once it is dropped. On Android and
Apple's platforms, where it is on, they only follow it while foreign keys are enforced. Indexes are dropped with the
old table, so create them again on the rebuilt one.

Drop the views that read the table before dropping it, and the views that read those, and the triggers of other tables
that write or read it, and create them again after the rename. Where `legacy_alter_table` is off, renaming a table
checks that every view and trigger still reads tables that exist, so it fails while one reads the dropped table.
Dropping and creating them again works on every platform. The triggers of the table itself are dropped with it, so
create them again too:

```kotlin
DROP(PersonNameView)  // A view that reads 'person'
// Create the new table, copy the rows, drop the old table and rename the new one, as above
CREATE_VIEW(PersonNameView) AS (PersonTable SELECT X<PersonName>())
```

The table `withName` returns has the columns, constraints and row type of the original, but no column properties, as
those name the original table. It is meant for statements on the table as a whole: `CREATE`, `INSERT`, `DROP` and
`ALTER_RENAME_TABLE_TO`.
For an [FTS table](advanced-query.md#full-text-search), it is an FTS table with the same
options, so an FTS table is rebuilt the same way, to change its columns or options.

The rows are converted by the `SELECT`, with a projection or with result columns, as described in
[Advanced Query](advanced-query.md), and [expressions](sql-functions.md#expressions): `CAST` converts a value's type, and
`ifnull` replaces a `NULL`, as in `ifnull(nickname, "")`.

## Insert

The class `Database` has overloaded function operator that type is `<T> Database.(Database.() -> T) -> T`. When you invoke
the operator function, it will produce a _DatabaseScope_. Yeah, that is an operator function's lambda parameter. Any SQL statement
must be written in _DatabaseScope_. And, the inner SQL statements only will be executed when the _DatabaseScope_ ended.

You already know, the _INSERT_, _DELETE_, _UPDATE_ and _SELECT_ SQL statements are used for table operation. So, before you write
your SQL statements, you also need to get a `Table` instance, like this:

```kotlin
private val database = Database(name = "Person.db", path = getGlobalPath(), version = 1)

fun sample() {
    database {
        PersonTable { table ->
            // Write your SQL statements...
        }
    }
}
```

The `PersonTable` is generated by _sqllin-processor_, because `Person` is annotated the `@DBRow`
annotation. Any class that is annotated the `@DBRow` will be generated a `Table` object, its name is
`class name + 'Table'`.

Now, let's do the real _INSERT_s:

```kotlin
fun sample() {
    database {
        PersonTable { table ->
            table INSERT Person(age = 4, name = "Tom")
            table INSERT listOf(
                Person(age = 10, name = "Nick"),
                Person(age = 3, name = "Jerry"),
                Person(age = 8, name = "Jack"),
            )
        }
    }
}
```

The _INSERT_ statements could insert objects directly. You can insert one or multiple objects once.

`INSERT_OR_IGNORE` skips the objects that conflict with a row already in the table, and `INSERT_OR_REPLACE` replaces
that row. They take the same arguments as `INSERT`.

_INSERT_ can also insert the rows a _SELECT_ returns, as `INSERT INTO ... SELECT` does, if the _SELECT_ reads rows of the
table's type. They can come from any table, through a projection or result columns:

```kotlin
fun sample() {
    database {
        // INSERT INTO person_archive(id,name,age) SELECT id,name,age FROM person WHERE age > ?
        PersonArchiveTable INSERT (PersonTable SELECT WHERE<PersonArchive>(PersonTable.age GT 60))
    }
}
```

The _SELECT_ becomes part of the _INSERT_, so it no longer runs on its own, and its results can't be read. The primary
key is copied as it is selected. `INSERT_OR_IGNORE` and `INSERT_OR_REPLACE` take a _SELECT_ as well.

_INSERT_ can also insert a row of [expressions](sql-functions.md#expressions), as `INSERT INTO ... VALUES` does, each
given to a property of the row type with `AS`. It is experimental:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        AuditTable { table ->
            // INSERT INTO audit(action,at) VALUES ('purge',datetime('now'))
            table INSERT listOf(literal("purge") AS Audit::action, datetime("now") AS Audit::at)
        }
    }
}
```

The columns left out take their default values, or NULL, which a `NOT NULL` column without a default rejects when the
statement runs. An expression that can be NULL can only be written to a nullable column, and the values can't read a
table, as SQLite doesn't allow it there: insert the values of other rows with a _SELECT_. `INSERT_OR_IGNORE` and
`INSERT_OR_REPLACE` take expressions as well.

`table INSERT DEFAULT_VALUES` inserts a row of the default values of all columns, as `INSERT INTO ... DEFAULT VALUES`
does: a column without a default value takes NULL, which a `NOT NULL` column rejects, and a key the database assigns
its next value. It is experimental, as are `INSERT_OR_IGNORE DEFAULT_VALUES` and `INSERT_OR_REPLACE DEFAULT_VALUES`.

## Delete

The _DELETE_ statements will be slightly more complex than _INSERT_. SQLlin doesn't delete objects like
[Jetpack Room](https://developer.android.com/training/data-storage/room), we use the _WHERE_ clause:

```kotlin
fun sample() {
    database {
        PersonTable { table ->
            table DELETE WHERE(age GTE 10 OR (name NEQ "Jerry"))
        }
    }
}
```

Let's understand the _WHERE_ clause. `WHERE` function receives a `ClauseCondiction` as a parameter. The `age` and `name` in the example are used for representing columns'
names, they are extension property with `Table`, their type are `ClauseElement`, and they are generated by _sqllin-processor_(KSP).

The `ClauseElement` has a series of operators that used for representing the SQL operators like: `=`, `>`, `<`, `LIKE`, `IN`, `IS` etc. When a `ClauseElement` invoke a
operator, we will get a `ClauseCondiction`. Multiple `ClauseCondiction`s would use the `AND` or `OR` operator to link and produce a new `ClauseCondiction`.

The chart of between SQL operators and SQLlin operators like this:

|SQL|SQLlin|
|---|---|
|=|EQ|
|!= |NEQ|
|<|LT|
|<=|LTE|
|>|GT|
|>=|GTE|
|BETWEEN|BETWEEN|
|IN|IN|
|LIKE|LIKE|
|GLOB|GLOB|
|OR|OR|
|AND|AND|
|LIKE ... ESCAPE|LIKE ... ESCAPE|

In a _LIKE_ pattern, `%` matches any characters and `_` any one character. To match them as themselves, give the pattern
an escape character with `ESCAPE`, which is experimental: after it, `%`, `_` and the escape character itself match only
themselves.

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        PersonTable { table ->
            // Deletes the people whose names have an underscore:
            // DELETE FROM person WHERE person.name LIKE ? ESCAPE ?, with "%\_%" and "\"
            table DELETE WHERE (name LIKE "%\\_%" ESCAPE '\\')
        }
    }
}
```

Sometimes, we want to delete all data in the table. At this time, the _DELETE_ statement doesn't have _WHERE_ clause:

```SQL
DELETE FROM person
```

In SQLlin we can write this to achieve the same effect:

```kotlin
fun sample() {
    database {
        PersonTable { table ->
            table DELETE X
        }
    }
}
```
The `X` is a Kotlin `object` (singleton).

## Update

The _UPDATE_ is similar with _DELETE_, it also use a _WHERE_ clause to limit update conditions. But, the
difference is the _UPDATE_ statement owns a particular _SET_ clause:

```kotlin
fun sample() {
    database {
        PersonTable { table ->
            table UPDATE SET { age = 5 } WHERE (name NEQ "Tom")
        }
    }
}
```

The _SET_ clause is different from other clauses, it receives a lambda as parameter, you can set the new value to column in the
lambda. The `age` in the set lambda is a writable property that also be generated by KSP, and it is only available in _SET_
clauses, it is different with readonly property `age` in _WHERE_ clauses.

The properties in the _SET_ lambda can only be assigned. Reading one, as in `age = age + 1`, is a compile error: it
doesn't hold the column's value, only a placeholder, so the column would be set to a value computed from that.

To set a column to an [expression](sql-functions.md#expressions), such as one computed from the row's columns, give
`SET` a list of expressions, each given to a property of the row type with `AS`, which checks its type at compile time.
It is experimental, so opt in with `@OptIn(ExperimentalDSLDatabaseAPI::class)`:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        PersonTable { table ->
            // UPDATE person SET age=(person.age + 1),name=(person.name || '!') WHERE person.name!=?
            table UPDATE SET(listOf((age + 1) AS Person::age, (name + "!") AS Person::name)) WHERE (name NEQ "Tom")
        }
    }
}
```

`SET(expression AS Person::age)` sets a single column. An expression that can be NULL can only set a nullable column,
and it can only read the columns of the table it updates, and not be an aggregate function, which is checked when the
statement is built. Use `literal(5)` for a value among expressions.

You also could write the _UPDATE_ statements without the _WHERE_ clause that used for update all rows, but you should use it with caution.

## Transaction

Using transaction is simple in SQLlin, you just need to use a `transaction {...}` wrap your SQL statements:

```kotlin
fun sample() {
    database {
        transaction {
            PersonTable { table ->
                table INSERT Person(age = 4, name = "Tom")
                table INSERT listOf(
                    Person(age = 10, name = "Nick"),
                    Person(age = 3, name = "Jerry"),
                    Person(age = 8, name = "Jack"),
                )
                table UPDATE SET { age = 5 } WHERE (name NEQ "Tom")
            }
        }
    }
}
```

The `transaction {...}` is a member function in `Database`, it is inside or outside of `TABLE(databaseName) {...}` doesn't matter.

## VACUUM

Deleting rows doesn't shrink the database file, as SQLite keeps the free pages to reuse them. `VACUUM()` rebuilds the
file, repacking it into the minimum disk space, and `VACUUM_INTO(file)` writes a vacuumed copy of the database to another
file: a backup, consistent as of one moment, of a database in use. Both are experimental:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class, PlatformDependentSQLiteAPI::class)
fun sample() {
    database {
        VACUUM()
    }
    database {
        VACUUM_INTO("/absolute/path/to/backup.db")
    }
}
```

SQLite can't vacuum inside a transaction, so both throw an `IllegalStateException` inside `transaction {...}`. VACUUM
rewrites the whole database, so it takes long for a large one and needs free disk space of up to twice its size, and it
may change the rowids of the tables without an INTEGER PRIMARY KEY. `VACUUM_INTO` needs SQLite 3.27.0, which Android has
from API 30 on, so it is marked with [`@PlatformDependentSQLiteAPI`](#apis-that-depend-on-the-sqlite-version). Its file
must not exist, and on Android its path has to be absolute.

## ANALYZE and REINDEX

`ANALYZE()` gathers the statistics of the tables and indexes, which the query planner chooses indexes with, and
`ANALYZE(table)` or `ANALYZE(name)` those of a table or an index. `PRAGMA_OPTIMIZE()` only gathers those that would help,
quickly, as SQLite advises to run before closing a database, or every few hours in a long-lived one; it needs SQLite
3.18.0, which Android has from API 26 on, so it is marked with
[`@PlatformDependentSQLiteAPI`](#apis-that-depend-on-the-sqlite-version). `REINDEX()` rebuilds all indexes, and
`REINDEX(table)`, `REINDEX(name)` or `REINDEX(collation)` those of a table, an index or a collation, as `NOCASE`. They
are experimental:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class, PlatformDependentSQLiteAPI::class)
fun sample() {
    database {
        ANALYZE(PersonTable)
        REINDEX("idx_person_name")
        PRAGMA_OPTIMIZE()
    }
}
```

## Next Step

You have learned how to use _INSERT_, _DELETE_ and _UPDATE_ statements. Next step you will learn _SELECT_ statements. The
_SELECT_ statement is more complex than other statements, be ready :).

- [Query](query.md)
- [Concurrency Safety](concurrency-safety.md)
- [SQL Functions](sql-functions.md)
- [Advanced Query](advanced-query.md)