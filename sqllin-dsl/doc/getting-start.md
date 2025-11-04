# Getting Started

中文版请见[这里](getting-start-cn.md)

**Welcome to use SQLlin !!!**

## Installation via Maven with Gradle

Add the dependencies of _sqllin-dsl_, _sqllin-driver_ and _sqllin-processor_ into your `build.gradle.kts`: 

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("com.google.devtools.ksp")
}

val sqllinVersion = "2.0.0"

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
```

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
                    PersonTable ALERT_ADD_COLUMN PersonTable.email
                }
            }
        }
    )
)
```

With `DSLDBConfiguration`, you can use CREATE, DROP, and ALTER operations directly in the callbacks, making schema management more type-safe and maintainable. The DSL operations available in these callbacks are the same as those available in regular `database { }` blocks.

Usually, you just need to create one `Database` instance in your component lifecycle. So, you need to close database manually when the lifecycle ended:

> Notice: `DSLDBConfiguration` is experimental, but it will completely replace `DatabaseConfiguration` when it is stable. That means _sqllin-dsl_ will not support to use `DatabaseConfiguration` to create `Database` instances in the future versions.

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

**Important type and nullability rules:**

- **For `Long` primary keys with auto-increment**: The property **must** be declared as nullable (`Long?`). This maps to SQLite's `INTEGER PRIMARY KEY` which acts as an alias for the internal `rowid`. When inserting a new record with `id = null`, SQLite automatically generates the ID.

- **For other types (String, Int, etc.)**: The property **must** be non-nullable. You must provide a unique value when inserting:

```kotlin
@DBRow
@Serializable
data class User(
    @PrimaryKey
    val username: String,  // Non-nullable, user-provided primary key
    val email: String,
)
```

The `autoIncrement` parameter enables stricter auto-incrementing behavior (using `AUTOINCREMENT` keyword), ensuring row IDs are never reused. This is only meaningful for `Long?` properties.

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

- You can apply `@CompositePrimaryKey` to **multiple properties** in the same class
- All properties with `@CompositePrimaryKey` **must be non-nullable**
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
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    @Unique @CollateNoCase val code: String,  // Unique and case-insensitive
    val name: String,
    val price: Double,
)
```

### Supported Types

SQLlin supports the following Kotlin types for properties in `@DBRow` data classes:

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
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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

## Next Step

You have learned all the preparations, you can start learn how to operate database now:

- [Modify Database and Transaction](modify-database-and-transaction.md)
- [Query](query.md)
- [Concurrency Safety](concurrency-safety.md)
- [SQL Functions](sql-functions.md)
- [Advanced Query](advanced-query.md)