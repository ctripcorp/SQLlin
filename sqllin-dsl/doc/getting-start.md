# Getting Started

中文版请见[这里](getting-start-cn.md)

**Welcome to use SQLlin !!!**

## Installation via Maven with Gradle

Add the dependencies of _sqllin-dsl_, _sqllin-driver_ and _sqllin-processor_ into your `build.gradle.kts`: 

```kotlin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

val sqllinVersion = "x.x.x" // Check latest version

kotlin {
    // ......
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                // sqllin-dsl
                implementation("com.ctrip.kotlin:sqllin-dsl:$sqllinVersion")
                // sqllin-driver
                implementation("com.ctrip.kotlin:sqllin-driver:$sqllinVersion")
                
                // The sqllin-dsl serialization and deserialization depends on kotlinx-serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")

                // Since 1.2.2, sqllin-dsl depends on kotlinx.coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        // ......
    }
}

// KSP dependencies
dependencies {
    // sqllin-processor
    add("kspCommonMainMetadata", "com.ctrip.kotlin:sqllin-processor:$sqllinVersion")
}

// The generated code is a source directory of commonMain, so every task that reads it has to run after KSP
afterEvaluate {
    tasks {
        matching { (it is KotlinCompilationTask<*> || it.name.startsWith("ksp")) && it.name != "kspCommonMainKotlinMetadata" }
            .configureEach { dependsOn("kspCommonMainKotlinMetadata") }
    }
}
```

The last block is required. The generated table objects are added to `commonMain` as a source directory, so every Kotlin
compilation reads the output of `kspCommonMainKotlinMetadata`, and so does every other KSP task when your project also runs
another KSP processor, such as Room or Koin Annotations. Gradle fails the build when a task reads the output of another task
without depending on it. KSP's own tasks are matched by name, because their types differ between KSP versions.

> Note: If you want to add dependencies of SQLlin into your Kotlin/Native executable program projects, sometimes you need to add the `linkerOpts`
> of SQLite into your `build.gradle.kts` correctly. You can refer to [issue #48](https://github.com/ctripcorp/SQLlin/issues/48) to get more information.

## Creating the Database

You can create the `Database` instance in sample:

```kotlin
import com.ctrip.sqllin.dsl.Database

val database = Database(name = "Person.db", path = getGlobalPath(), version = 1)
```

The `DatabasePath` is the second parameter `path`'s type, it is represented differently on different platforms.
On Android, you can get it through [`Context`](https://developer.android.com/reference/android/content/Context), and you can get it through string on native platforms.
For example, you can define a expect function in your common source set:

```kotlin
import com.ctrip.sqllin.driver.DatabasePath

expect fun getGlobalDatabasePath(): DatabasePath
```

In your Android source set, you can implement it by:

```kotlin
import android.content.Context
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.driver.toDatabasePath

actual fun getGlobalDatabasePath(): DatabasePath =
    applicationContext.toDatabasePath() 
    
val applicationContext: Context
    get() {
        // Use your own way to get `applicationContext`
    }
```

In your iOS source set (similar with other Apple platforms), you can implement it by:

```kotlin
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.driver.toDatabasePath

actual fun getGlobalDatabasePath(): DatabasePath =
    (NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, 
        NSUserDomainMask, true).firstOrNull() as? String ?: ""
    ).toDatabasePath()

```

You can config more SQLite arguments when you create the `Database` instance:

```kotlin
import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.dsl.Database

val database = Database(
    DatabaseConfiguration(
        name = "Person.db",
        path = getGlobalDatabasePath(),
        version = 1,
        isReadOnly = false,
        inMemory = false,
        journalMode = JournalMode.WAL,
        synchronousMode = SynchronousMode.NORMAL,
        busyTimeout = 5000,
        lookasideSlotSize = 0,
        lookasideSlotCount = 0,
        create = {
            it.execSQL("create table person (id integer primary key autoincrement, name text, age integer)")
        },
        upgrade = { databaseConnection, oldVersion, newVersion -> }
    )
)
```

Note, because of limitation by Android Framework, the `inMemory`, `busyTimeout`, `lookasideSlotSize`, `lookasideSlotCount`
only work on Android 9 and higher. And, because [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc)(SQLlin is based on it on JVM) doesn't support
`sqlite3_config()`, the `lookasideSlotSize` and `lookasideSlotCount` don't work on JVM target.

### Using DSLDBConfiguration for Type-Safe Schema Management

Alternatively, you can use `DSLDBConfiguration` which allows you to use the type-safe SQL DSL in the `create` and `upgrade` callbacks instead of raw SQL strings:

```kotlin
import com.ctrip.sqllin.driver.DSLDBConfiguration
import com.ctrip.sqllin.dsl.Database

val database = Database(
    DSLDBConfiguration(
        name = "Person.db",
        path = getGlobalDatabasePath(),
        version = 1,
        isReadOnly = false,
        inMemory = false,
        journalMode = JournalMode.WAL,
        synchronousMode = SynchronousMode.NORMAL,
        busyTimeout = 5000,
        lookasideSlotSize = 0,
        lookasideSlotCount = 0,
        create = {
            // Use type-safe DSL instead of raw SQL
            CREATE(PersonTable)
        },
        upgrade = { oldVersion, newVersion ->
            when (oldVersion) {
                1 -> {
                    // Example: Add a new column in version 2
                    PersonTable ALTER_ADD_COLUMN PersonTable.email
                }
            }
        }
    )
)
```

With `DSLDBConfiguration`, you can use CREATE, DROP, and ALTER operations directly in the callbacks, making schema management more type-safe and maintainable. The DSL operations available in these callbacks are the same as those available in regular `database { }` blocks.

Usually, you just need to create one `Database` instance in your component lifecycle. So, you need to close database manually when the lifecycle ended:

> Notice: `DSLDBConfiguration` will completely replace `DatabaseConfiguration`. That means _sqllin-dsl_ will not support to use `DatabaseConfiguration` to create `Database` instances in the future versions.

```kotlin
override fun onDestroy() {
    database.close()
}
```

## Defining Your database entity

In _sqllin-dsl_, you can insert and query objects directly. So, you need to use the correct way to define your data classes. For example:

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import kotlinx.serialization.Serializable

@DBRow(tableName = "person")
@Serializable
data class Person(
    val name: String,
    val age: Int,
)
```

Your database entities' property names should be same with the database table's column names. The database entities cannot have properties with names different from all
column names in the table. But the count of your database entities' properties can less than the count of columns(only when you don't need to use _sqllin-dsl_ to create the tables).

The `@DBRow`'s param `tableName` represents the table name in Database, please ensure pass
the correct value. If you don't pass the parameter manually, _sqllin-processor_ will use the class
name as table name, for example, `Person`'s default table name is "Person".

For each `@DBRow` class, _sqllin-processor_ generates an object named after the class with a `Table` suffix, such as
`PersonTable` for `Person`, whatever its `tableName` is. That object is what you write SQL against with the DSL.

In _sqllin-dsl_, objects are serialized to SQL and deserialized from cursor depend on _kotlinx.serialization_. So, you also need to add the `@Serializable` onto your data classes. Therefore, if
you want to ignore some properties when serialization or deserialization and `Table` classes generation, you can annotate your properties with `kotlinx.serialization.Transient`.

### Defining Primary Keys

SQLlin provides annotations to define primary keys for your database tables.

#### Single Primary Key with @PrimaryKey

Use `@PrimaryKey` to mark a single property as the primary key:

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class Person(
    @PrimaryKey(autoIncrement = true)
    val id: Long? = null,  // Auto-incrementing primary key
    val name: String,
    val age: Int,
)
```

**Important type and nullability rules:** the nullability of the property decides who supplies the key's value.

- **`Long?`, assigned by the database**: This maps to SQLite's `INTEGER PRIMARY KEY`, which acts as an alias for the internal `rowid`. When inserting a new record with `id = null`, SQLite automatically generates the ID.

- **`Long`, supplied by you**: This also maps to `INTEGER PRIMARY KEY`, so it is still a `rowid` alias, but every insert writes the value you provide. Use it for numeric keys that come from elsewhere, such as IDs assigned by a remote service:

```kotlin
@DBRow
@Serializable
data class Movie(
    @PrimaryKey
    val id: Long,  // Non-nullable, user-provided, still a rowid alias
    val title: String,
)
```

- **Other types (String, Int, etc.), supplied by you**: The property **must** be non-nullable, and maps to a column such as `TEXT PRIMARY KEY NOT NULL`. A nullable primary key of any type other than `Long` is a compile-time error. You must provide a unique value when inserting:

```kotlin
@DBRow
@Serializable
data class User(
    @PrimaryKey
    val username: String,  // Non-nullable, user-provided primary key
    val email: String,
)
```

The `autoIncrement` parameter enables stricter auto-incrementing behavior (using `AUTOINCREMENT` keyword), ensuring row IDs are never reused. It requires a `Long?` property, the only kind of key the database assigns.

#### Composite Primary Key with @CompositePrimaryKey

Use `@CompositePrimaryKey` when your table's primary key consists of multiple columns:

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class Enrollment(
    @CompositePrimaryKey
    val studentId: Long,
    @CompositePrimaryKey
    val courseId: Long,
    val enrollmentDate: String,
)
```

**Important rules:**

- Apply `@CompositePrimaryKey` to **at least two properties** in the same class; annotating only one is a compile-time error, since a single-column primary key is declared with `@PrimaryKey`
- All properties with `@CompositePrimaryKey` **must be non-nullable**, and are declared `NOT NULL` in the generated table
- You **cannot** mix `@PrimaryKey` and `@CompositePrimaryKey` in the same class - use one or the other
- The combination of all `@CompositePrimaryKey` properties forms the table's composite primary key

### Column Constraints and Modifiers

SQLlin provides several annotations to add constraints and modifiers to your table columns.

#### @Unique - Single Column Uniqueness

Use `@Unique` to enforce that no two rows can have the same value in a column:

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.Unique
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class User(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @Unique val email: String,        // Each email must be unique
    @Unique val username: String,     // Each username must be unique
    val displayName: String,
)
// Generated SQL: CREATE TABLE User(
//   id INTEGER PRIMARY KEY AUTOINCREMENT,
//   email TEXT UNIQUE,
//   username TEXT UNIQUE,
//   displayName TEXT
// )
```

**Important notes:**
- Multiple NULL values are allowed in a UNIQUE column (NULL is not equal to NULL in SQL)
- To prevent NULL values, use a non-nullable type: `val email: String`

#### @CompositeUnique - Multi-Column Uniqueness

Use `@CompositeUnique` to ensure that the **combination** of multiple columns is unique:

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.CompositeUnique
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class Enrollment(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @CompositeUnique(0) val studentId: Int,
    @CompositeUnique(0) val courseId: Int,
    val enrollmentDate: String,
)
// Generated SQL: CREATE TABLE Enrollment(
//   id INTEGER PRIMARY KEY AUTOINCREMENT,
//   studentId INT,
//   courseId INT,
//   enrollmentDate TEXT,
//   UNIQUE(studentId, courseId)
// )
// A student cannot enroll in the same course twice
```

**Grouping:** Properties can belong to multiple unique constraint groups by specifying different group numbers:

```kotlin
@DBRow
@Serializable
data class Event(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @CompositeUnique(0, 1) val userId: Int,     // Part of groups 0 and 1
    @CompositeUnique(0) val eventType: String,  // Part of group 0
    @CompositeUnique(1) val timestamp: Long,    // Part of group 1
)
// Generated SQL: CREATE TABLE Event(
//   id INTEGER PRIMARY KEY AUTOINCREMENT,
//   userId INT,
//   eventType TEXT,
//   timestamp BIGINT,
//   UNIQUE(userId, eventType),    // Group 0: userId + eventType
//   UNIQUE(userId, timestamp)     // Group 1: userId + timestamp
// )
```

**Default behavior:**
- If no group is specified: `@CompositeUnique()`, defaults to group `0`
- All properties with the same group number are combined into a single composite constraint

#### @CollateNoCase - Case-Insensitive Text Comparison

Use `@CollateNoCase` to make string comparisons case-insensitive:

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.NoCase
import com.ctrip.sqllin.dsl.annotation.Unique
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class User(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @CollateNoCase @Unique val email: String,  // Case-insensitive unique email
    @CollateNoCase val username: String,        // Case-insensitive username
    val bio: String,
)
// Generated SQL: CREATE TABLE User(
//   id INTEGER PRIMARY KEY AUTOINCREMENT,
//   email TEXT COLLATE NOCASE UNIQUE,
//   username TEXT COLLATE NOCASE,
//   bio TEXT
// )
```

**Type restrictions:**
- Can **only** be applied to `String` or `Char` properties (and their nullable variants)
- Attempting to use on non-text types will result in a compile-time error

**SQLite behavior with COLLATE NOCASE:**
- `'ABC' = 'abc'` evaluates to true
- `ORDER BY` clauses sort case-insensitively
- Indexes on the column are case-insensitive

#### Combining Multiple Constraints

You can combine multiple constraint annotations on the same property:

```kotlin
@DBRow
@Serializable
data class Product(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @Unique @CollateNoCase val code: String,  // Unique and case-insensitive
    val name: String,
    val price: Double,
)
```

#### @Default - Column Default Values

Use `@Default` to specify default values for columns in your CREATE TABLE statements. SQLite will automatically use these values when inserting rows without explicitly providing values for these columns:

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.Default
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class User(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val name: String,
    @Default("'active'") val status: String,              // String default
    @Default("0") val loginCount: Int,                     // Numeric default
    @Default("1") val isEnabled: Boolean,                  // Boolean default (1 = true)
    @Default("CURRENT_TIMESTAMP") val createdAt: String,   // SQLite function
)
// Generated SQL: CREATE TABLE User(
//   id INTEGER PRIMARY KEY AUTOINCREMENT,
//   name TEXT NOT NULL,
//   status TEXT NOT NULL DEFAULT 'active',
//   loginCount INT NOT NULL DEFAULT 0,
//   isEnabled INT NOT NULL DEFAULT 1,
//   createdAt TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
// )
```

**Value format:**
- **Strings**: Must be enclosed in single quotes: `'default text'`
- **Numbers**: Plain numeric literals: `0`, `42`, `3.14`
- **Booleans**: Use `0` for false or `1` for true
- **NULL**: Use the literal `NULL`
- **Expressions**: SQLite functions like `CURRENT_TIMESTAMP`, `datetime('now')`, `(random())`, etc.

**Integration with Foreign Key Triggers:**

Default values are **required** when using `ON_DELETE_SET_DEFAULT` or `ON_UPDATE_SET_DEFAULT` triggers:

```kotlin
@DBRow
@Serializable
data class Order(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(
        tableName = "User",
        foreignKeys = ["id"],
        trigger = Trigger.ON_DELETE_SET_DEFAULT
    )
    @Default("0")  // REQUIRED when using ON_DELETE_SET_DEFAULT
    val userId: Long,
    val amount: Double,
)
// When a User is deleted, their Orders' userId becomes 0
```

**Important notes:**
- **String values must use single quotes**: `'text'`, not `"text"`
- Default values don't override explicitly provided values in INSERT statements
- Functions like `CURRENT_TIMESTAMP` are evaluated at insertion time, not at table creation
- The annotation processor doesn't validate that the default value matches the column type

**Common pitfall:**

```kotlin
// ❌ Wrong - using double quotes for strings
@Default("\"active\"")
val status: String

// ✅ Correct - using single quotes for strings
@Default("'active'")
val status: String
```

#### @Check - Validating Values

Use `@Check` to add a `CHECK` constraint: an SQL expression that every row has to satisfy. SQLite evaluates it whenever
an _INSERT_ or _UPDATE_ writes a row, and rejects the row if it is false, so invalid data can't get into the table,
whichever code writes it. `@Check` is experimental, so opt in with `@OptIn(ExperimentalDSLDatabaseAPI::class)`:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBRow
@Check("endDate IS NULL OR endDate >= startDate", constraintName = "valid_period")
@Serializable
data class Membership(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @Check("length(trim(name)) > 0") val name: String,
    @Check("level IN (0, 1, 2)") val level: Int,
    @Check("age BETWEEN 0 AND 150") val age: Int?,
    val startDate: String,
    val endDate: String?,
)
// Generated SQL: CREATE TABLE Membership(
//   id INTEGER PRIMARY KEY AUTOINCREMENT,
//   name TEXT NOT NULL CHECK (length(trim(name)) > 0),
//   level INT NOT NULL CHECK (level IN (0, 1, 2)),
//   age INT CHECK (age BETWEEN 0 AND 150),
//   startDate TEXT NOT NULL,
//   endDate TEXT,
//   CONSTRAINT valid_period CHECK (endDate IS NULL OR endDate >= startDate)
// )
```

On a property, `@Check` adds a constraint to its column; on the class, a constraint to the table. SQLite checks them
alike, and both can refer to any column, so a constraint that compares columns reads best on the class. Both can be
repeated, and every constraint is checked.

**Naming constraints:**

`constraintName` names a constraint, which SQLite then reports when a row fails it:
`CHECK constraint failed: valid_period`. An unnamed constraint is reported by its expression from SQLite 3.34 on, but by
the table's name before, as on Android below API 34, where only names tell a table's constraints apart. A name has to
be an SQL identifier, and the names of a table's CHECK constraints have to differ.

**NULL passes:**

A row fails a constraint only when its expression is false, and when it is NULL, the row passes. So
`@Check("age BETWEEN 0 AND 150")` lets a NULL age through; to keep NULL out, make the property non-null. This also
catches expressions that look right:

```kotlin
// ❌ Lets every text that isn't a date through: date(day) is NULL for it, and so is the comparison
@Check("date(day) = day")
val day: String?

// ✅ Keeps them out: IS compares NULL as a value
@Check("day IS NULL OR date(day) IS day")
val day: String?
```

**The expression:**
- It is SQL, written into the CREATE TABLE statement as it is, so a mistake in it only shows when the table is created
- It names columns by their names in the table
- It can't contain a subquery, so it can't refer to other tables: use [foreign keys](#foreign-key-constraints) for that
- It can only call deterministic functions: a comparison with the current time, such as `expires > date('now')`, fails
  when a row is written

**How statements behave:**
- An _INSERT_ or _UPDATE_ that writes a row failing a constraint fails with the driver's exception, and the _UPDATE_
  leaves the row as it was
- `INSERT_OR_IGNORE` skips the rows failing a constraint, and inserts the others
- `INSERT_OR_REPLACE` fails as an _INSERT_ does: replacing a row doesn't help a row satisfy a constraint

**Existing tables:**

SQLite's `ALTER TABLE` can't add a constraint to a table, and `ALTER_ADD_COLUMN` adds a column without its constraints.
To add a constraint to an existing table, [rebuild the table](modify-database-and-transaction.md#rebuilding-a-table).
When a column is dropped, its own constraints are dropped with it, but SQLite refuses to drop a column that another
column's constraint or the table's refers to.

### Supported Types

SQLlin supports the following Kotlin types for properties in `@DBRow` data classes. A property of any other type is a compile-time error; to keep such a property out of the table, annotate it with `kotlinx.serialization.Transient`:

#### Numeric Types
- **Integer types:** `Byte`, `Short`, `Int`, `Long`
- **Unsigned integer types:** `UByte`, `UShort`, `UInt`, `ULong`
- **Floating-point types:** `Float`, `Double`

#### Text Types
- `String` - Maps to SQLite TEXT
- `Char` - Maps to SQLite CHAR(1)

#### Other Types
- `Boolean` - Maps to SQLite BOOLEAN (stored as 0 or 1)
- `ByteArray` - Maps to SQLite BLOB (for binary data)
- **Enum classes** - Maps to SQLite INT (stored as ordinal values)

#### Type Aliases
- Any typealias of the supported types above
- Typealiases can be nested (typealias of another typealias)

```kotlin
typealias UserId = Long
typealias Price = Double
typealias Age = Int

// You can also create typealiases of other typealiases
typealias AccountId = UserId

@DBRow
@Serializable
data class Product(
    @PrimaryKey val id: UserId,      // Works! Typealias of Long
    val name: String,
    val price: Price,                // Works! Typealias of Double
    val ownerId: AccountId,          // Works! Typealias of typealias
)
```

**Important notes:**
- The processor resolves typealiases recursively to find the underlying type
- The underlying type must be one of the supported types listed above

#### Nullable Types
- All of the above types can be nullable (e.g., `String?`, `Int?`, `Boolean?`)
- Exception: Primary keys have special nullability rules (see Primary Key section)

#### Enum Example

```kotlin
enum class UserStatus {
    ACTIVE, INACTIVE, SUSPENDED, BANNED
}

@DBRow
@Serializable
data class User(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val username: String,
    val status: UserStatus,         // Stored as 0, 1, 2, or 3
    val priority: Priority?,        // Nullable enum is also supported
)
```

**Important notes:**
- Enum values are stored as their ordinal (integer) values
- Changing the order of enum constants will affect the stored values
- Consider using String if you need more stable storage

#### SQLite Type Mappings

| Kotlin Type | SQLite Type |
|------------|-------------|
| Byte, UByte | TINYINT |
| Short, UShort | SMALLINT |
| Int, UInt | INT |
| Long | BIGINT (INTEGER if primary key) |
| ULong | BIGINT |
| Float | FLOAT |
| Double | DOUBLE |
| Boolean | BOOLEAN |
| Char | CHAR(1) |
| String | TEXT |
| ByteArray | BLOB |
| Enum | INT |

### Foreign Key Constraints

SQLlin provides comprehensive support for foreign key constraints to maintain referential integrity between tables. Foreign keys ensure that relationships between tables remain consistent by enforcing rules when data is inserted, updated, or deleted.

#### Important: Enabling Foreign Keys

By default, SQLite **does not enforce** foreign key constraints for backward compatibility. You must explicitly enable foreign key enforcement using `PRAGMA_FOREIGN_KEYS(true)` before creating tables:

```kotlin
database {
    // CRITICAL: Enable foreign key enforcement first
    PRAGMA_FOREIGN_KEYS(true)

    // Now create tables with foreign keys
    CREATE(UserTable)
    CREATE(OrderTable)  // Has foreign key to UserTable
}
```

**Key points:**
- This setting is **per-connection** and must be set each time you open the database
- The setting **cannot be changed** inside a transaction
- Without enabling this, foreign keys will be part of the schema but **not enforced**

#### Defining Foreign Keys

SQLlin provides two approaches for defining foreign keys:

##### Approach 1: Column-Level with @References

Use `@References` for simple single-column foreign keys. This is the **recommended approach** for most use cases:

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.References
import com.ctrip.sqllin.dsl.annotation.Trigger
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class User(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val name: String,
    val email: String,
)

@DBRow
@Serializable
data class Order(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(
        tableName = "User",
        foreignKeys = ["id"],
        trigger = Trigger.ON_DELETE_CASCADE
    )
    val userId: Long,
    val amount: Double,
    val orderDate: String,
)
// Generated SQL: CREATE TABLE Order(
//   id INTEGER PRIMARY KEY AUTOINCREMENT,
//   userId BIGINT REFERENCES User(id) ON DELETE CASCADE,
//   amount DOUBLE,
//   orderDate TEXT
// )
```

##### Approach 2: Table-Level with @ForeignKeyGroup + @ForeignKey

Use this approach for composite foreign keys that reference multiple columns:

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey
import com.ctrip.sqllin.dsl.annotation.ForeignKeyGroup
import com.ctrip.sqllin.dsl.annotation.ForeignKey
import com.ctrip.sqllin.dsl.annotation.Trigger
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class Product(
    @CompositePrimaryKey val categoryId: Int,
    @CompositePrimaryKey val productCode: String,
    val name: String,
    val price: Double,
)

@DBRow
@Serializable
@ForeignKeyGroup(
    group = 0,
    tableName = "Product",
    trigger = Trigger.ON_DELETE_CASCADE,
    constraintName = "fk_product"
)
data class OrderItem(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @ForeignKey(group = 0, reference = "categoryId")
    val productCategory: Int,
    @ForeignKey(group = 0, reference = "productCode")
    val productCode: String,
    val quantity: Int,
)
// Generated SQL: CREATE TABLE OrderItem(
//   id INTEGER PRIMARY KEY AUTOINCREMENT,
//   productCategory INT,
//   productCode TEXT,
//   quantity INT,
//   CONSTRAINT fk_product FOREIGN KEY (productCategory,productCode)
//     REFERENCES Product(categoryId,productCode) ON DELETE CASCADE
// )
```

#### Referential Actions (Triggers)

Triggers define what happens when a referenced row is deleted or updated. SQLlin supports all standard SQLite triggers via the `Trigger` enum:

##### DELETE Triggers

**ON_DELETE_CASCADE**: Automatically delete child rows when parent is deleted
```kotlin
@DBRow
@Serializable
data class Order(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_CASCADE)
    val userId: Long,
    val amount: Double,
)
// When a User is deleted, all their Orders are automatically deleted
```

**ON_DELETE_SET_NULL**: Set foreign key to NULL when parent is deleted (requires nullable column)
```kotlin
@DBRow
@Serializable
data class Post(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_SET_NULL)
    val authorId: Long?,  // Must be nullable!
    val content: String,
)
// When a User is deleted, their Posts remain but authorId becomes NULL
```

**ON_DELETE_RESTRICT**: Prevent deletion of parent if children exist
```kotlin
@DBRow
@Serializable
data class OrderItem(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(tableName = "Order", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_RESTRICT)
    val orderId: Long,
    val productId: Long,
)
// An Order cannot be deleted if it has OrderItems
```

**ON_DELETE_SET_DEFAULT**: Set foreign key to its default value when parent is deleted
```kotlin
@DBRow
@Serializable
data class Comment(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_SET_DEFAULT)
    val userId: Long = 0L,  // Default to 0 (anonymous user)
    val content: String,
)
```

##### UPDATE Triggers

The same actions are available for UPDATE operations:
- `ON_UPDATE_CASCADE`: Update child foreign keys when parent primary key changes
- `ON_UPDATE_SET_NULL`: Set child foreign keys to NULL (requires nullable column)
- `ON_UPDATE_RESTRICT`: Prevent updating parent primary key if children exist
- `ON_UPDATE_SET_DEFAULT`: Set child foreign keys to default value

##### Trigger Behavior Summary

| Trigger | Parent Deleted/Updated | Child Behavior | Nullable Required? |
|---------|------------------------|----------------|-------------------|
| NULL (default) | Allowed | No change | No |
| CASCADE | Allowed | Child rows deleted/updated | No |
| SET_NULL | Allowed | Foreign key set to NULL | **Yes** |
| SET_DEFAULT | Allowed | Foreign key set to DEFAULT | No |
| RESTRICT | **Prevented** | Operation fails | No |

#### Multiple Foreign Keys

A table can have multiple foreign key constraints to different parent tables:

```kotlin
@DBRow
@Serializable
@ForeignKeyGroup(group = 0, tableName = "User", trigger = Trigger.ON_DELETE_CASCADE)
@ForeignKeyGroup(group = 1, tableName = "Product", trigger = Trigger.ON_DELETE_RESTRICT)
data class OrderItem(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @ForeignKey(group = 0, reference = "id") val userId: Long,
    @ForeignKey(group = 1, reference = "id") val productId: Long,
    val quantity: Int,
)
// Generated SQL: CREATE TABLE OrderItem(
//   id INTEGER PRIMARY KEY AUTOINCREMENT,
//   userId BIGINT,
//   productId BIGINT,
//   quantity INT,
//   FOREIGN KEY (userId) REFERENCES User(id) ON DELETE CASCADE,
//   FOREIGN KEY (productId) REFERENCES Product(id) ON DELETE RESTRICT
// )
```

Or using `@References`:
```kotlin
@DBRow
@Serializable
data class OrderItem(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_CASCADE)
    val userId: Long,
    @References(tableName = "Product", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_RESTRICT)
    val productId: Long,
    val quantity: Int,
)
```

#### Named Constraints

You can optionally name your foreign key constraints for better error messages and schema introspection:

```kotlin
@DBRow
@Serializable
data class Order(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(
        tableName = "User",
        foreignKeys = ["id"],
        trigger = Trigger.ON_DELETE_CASCADE,
        constraintName = "fk_order_user"  // Optional constraint name
    )
    val userId: Long,
)
// Generated SQL: userId BIGINT CONSTRAINT fk_order_user REFERENCES User(id) ON DELETE CASCADE
```

#### Deferred Foreign Keys

SQLite checks a foreign key after each statement. With `deferred = true`, on `@References` or `@ForeignKeyGroup`, it
checks it when the transaction commits instead, as `DEFERRABLE INITIALLY DEFERRED` does, so that a transaction can
insert a child before its parent, or rows that refer to each other:

```kotlin
@DBRow
@Serializable
data class Order(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], deferred = true)
    val userId: Long,
)
// Generated SQL: userId BIGINT REFERENCES User(id) DEFERRABLE INITIALLY DEFERRED
```

A transaction that commits while such a key isn't satisfied fails, and is rolled back. Outside a transaction, each
statement is its own transaction, so it is checked right away.

#### Best Practices

1. **Always enable foreign keys**: Call `PRAGMA_FOREIGN_KEYS(true)` at the start of each database session
2. **Create parent tables first**: Create referenced tables before creating tables with foreign keys to them
3. **Use CASCADE for dependent data**: Use `ON_DELETE_CASCADE` when child data should not exist without its parent
4. **Use SET_NULL for optional relationships**: Use `ON_DELETE_SET_NULL` when child data can exist independently
5. **Use RESTRICT for protection**: Use `ON_DELETE_RESTRICT` to prevent accidental deletion of parent data
6. **Consider nullable columns**: Use nullable foreign key columns when the relationship is optional
7. **Name your constraints**: Use `constraintName` parameter for better debugging and error messages

#### Complete Example

Here's a complete example demonstrating foreign key relationships:

```kotlin
import com.ctrip.sqllin.dsl.Database
import com.ctrip.sqllin.dsl.annotation.*
import kotlinx.serialization.Serializable

// Parent table: Users
@DBRow
@Serializable
data class User(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @Unique val email: String,
    val name: String,
)

// Child table: Orders with CASCADE delete
@DBRow
@Serializable
data class Order(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_CASCADE)
    val userId: Long,
    val amount: Double,
    val orderDate: String,
)

// Child table: Posts with SET_NULL delete
@DBRow
@Serializable
data class Post(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_SET_NULL)
    val authorId: Long?,  // Nullable - posts can exist without author
    val title: String,
    val content: String,
)

fun setupDatabase() {
    database {
        // CRITICAL: Enable foreign key enforcement
        PRAGMA_FOREIGN_KEYS(true)

        // Create parent table first
        CREATE(UserTable)

        // Then create child tables
        CREATE(OrderTable)
        CREATE(PostTable)

        // Insert some data
        val user = User(id = null, email = "alice@example.com", name = "Alice")
        UserTable INSERT user

        val order = Order(id = null, userId = 1L, amount = 99.99, orderDate = "2025-01-15")
        OrderTable INSERT order

        // This will fail because user 999 doesn't exist (foreign key violation)
        try {
            val invalidOrder = Order(id = null, userId = 999L, amount = 50.0, orderDate = "2025-01-15")
            OrderTable INSERT invalidOrder  // Throws exception!
        } catch (e: Exception) {
            println("Foreign key constraint violation: ${e.message}")
        }

        // Delete the user - CASCADE will delete their orders, SET_NULL will null post authors
        UserTable DELETE WHERE(UserTable.id EQ 1L)
        // All orders for user 1 are automatically deleted
        // All posts by user 1 have authorId set to NULL
    }
}
```

## Next Step

You have learned all the preparations, you can start learn how to operate database now:

- [Modify Database and Transaction](modify-database-and-transaction.md)
- [Query](query.md)
- [Concurrency Safety](concurrency-safety.md)
- [SQL Functions](sql-functions.md)
- [Advanced Query](advanced-query.md)