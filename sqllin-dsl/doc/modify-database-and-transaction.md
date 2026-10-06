# Modify Database and Transaction

中文版请见[这里](modify-database-and-transaction-cn.md)

In [Getting Start](getting-start.md), we have learned how to create the `Database` instance and define your database entities. Now,
we start to learn how to write SQL statements with SQLlin.

## Table Structure Operations

SQLlin provides type-safe DSL operations for managing table structures: CREATE, DROP, and ALTER, and for views.

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
        // CREATE VIEW adults(name,age) AS SELECT name,age FROM person WHERE age>=18
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

#### Rename Column

Rename a column within a table:

```kotlin
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

Remove a column from an existing table:

```kotlin
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
                    // INSERT INTO person_new(id,fullName) SELECT id,name AS fullName FROM person
                    newPerson INSERT (table SELECT (name AS Person::fullName))
                }
                DROP(PersonV1Table)
                "person_new" ALTER_RENAME_TABLE_TO PersonTable
            }
        }
    )
)
```

Rename the new table, not the old one. Renaming a table also renames the references to it in the foreign keys of
other tables, with SQLite's default settings, so the references would follow the old table, and be left pointing at a
table that no longer exists once it is dropped. Indexes are dropped with the old table, so create them again on the
rebuilt one.

The table `withName` returns has the columns, constraints and row type of the original, but no column properties, as
those name the original table. It is meant for statements on the table as a whole: `CREATE`, `INSERT`, `DROP` and
`ALTER_RENAME_TABLE_TO`.
For an [FTS table](advanced-query.md#full-text-search), it is an FTS table with the same
options, so an FTS table is rebuilt the same way, to change its columns or options.

The rows are converted by the `SELECT`, with a projection or with result columns, as described in
[Advanced Query](advanced-query.md). Functions to convert a value's type, or to replace a `NULL`, aren't available yet.

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

## Next Step

You have learned how to use _INSERT_, _DELETE_ and _UPDATE_ statements. Next step you will learn _SELECT_ statements. The
_SELECT_ statement is more complex than other statements, be ready :).

- [Query](query.md)
- [Concurrency Safety](concurrency-safety.md)
- [SQL Functions](sql-functions.md)
- [Advanced Query](advanced-query.md)