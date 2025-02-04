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

val sqllinVersion = "1.4.1"

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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")

                // Since 1.2.2, sqllin-dsl depends on kotlinx.coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
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

Now, the operations that change database structure haven't been supported by DSL yet. So, you need to write these SQL statements by string
as in `create` and `upgrade` parameters.

Usually, you just need to create one `Database` instance in your component lifecycle. So, you need to close database manually when the lifecycle ended:

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
column names in the table. But the count of your database entities' properties can less than the count of columns.

The `@DBRow`'s param `tableName` represents the table name in Database, please ensure pass
the correct value. If you don't pass the parameter manually, _sqllin-processor_ will use the class
name as table name, for example, `Person`'s default table name is "Person".

In _sqllin-dsl_, objects are serialized to SQL and deserialized from cursor depend on _kotlinx.serialization_. So, you also need to add the `@Serializable` onto your data classes. Therefore, if
you want to ignore some properties when serialization or deserialization and `Table` classes generation, you can annotate your properties with `kotlinx.serialization.Transient`.

## Next Step

You have learned all the preparations, you can start learn how to operate database now:

- [Modify Database and Transaction](modify-database-and-transaction.md)
- [Query](query.md)
- [Concurrency Safety](concurrency-safety.md)
- [SQL Functions](sql-functions.md)
- [Advanced Query](advanced-query.md)