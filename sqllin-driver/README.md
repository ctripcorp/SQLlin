# The sqllin-driver basic design and usage

中文版请见[这里](README_CN.md)

## Design

Initially, we need a multiplatform available low-level Kotlin API to call SQLite. Because we think _sqllin-dsl_
should is platform independent. So, we need the _sqllin-driver_, and _sqllin-dsl_ based on it. Our goal is
writing the common API in Kotlin Multiplatform common source set and they have different implementation in
different platforms.

In Android, not many ways to choose from. If we use the Android Framework SQLite Java API, everything will be simple,
but defect is many SQLite parameters cannot take effect on systems below Android P. If we writing JNI code
to call SQLite C functions by ourselves, above problem will be resolved, but this will lead to a bigger problem:
In systems above Android N, Google doesn't allow developers call system built-in SQLite C function in NDK. If
we firmly choose this way, we have to compile the SQLite source code into _sqllin-driver_, this will complicate
our project. Finally we still choose based on Android Framework Java API.

In Native platforms, things look different. We can call SQLite C API directly, this is the most intuitive way.
The ability of Kotlin/Native interop with C is very perfect, but in Kotlin/Native you must use some very difficult
to understanding API to complete interop with C, like: `memScoped`, `CPointer`, `CPointerVarOf`, `toKString` etc..
In this time, I found the [SQLiter](https://github.com/touchlab/SQLiter), that's a Kotlin/Native multiplatform
library. If I use it, I can put the Kotlin-C interop translate to Kotlin language-internal calls. It is very
convenient. 

By the way, [SQLiter](https://github.com/touchlab/SQLiter) also is the driver that
[SQLDelight](https://github.com/cashapp/sqldelight) to call SQLite C library in native platforms. It is not only
supports iOS, it also supports all the operating systems of Apple, Linux(x64) and Windows(mingwX86, mingwX64).

## Basic usage

I am not advice you use _sqllin-driver_ in your application projects directly, but if you want to develop your own SQLite
higher-level API library, you can use it.

### Installation via Maven in Gradle

```kotlin
kotlin {
    // ......
    sourceSets {
        val commonMain by getting {
            dependencies {
                // sqllin-driver
                implementation("com.ctrip.kotlin:sqllin-driver:$sqllinVersion")
            }
        }
        // ......
    }
}
```

### Open and Close Database

```kotlin
// Open SQLite
val databaseConnection = openDatabase(
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
// Close SQLite
databaseConnection.close()
```

You can deploy many SQLite parameters in `DatabaseConfiguration`, their means just like their names.

### CRUD

```kotlin
// INSERT
databaseConnection.executeInsert(SQL.INSERT, arrayOf(20, "Tom"))

// DELETE
databaseConnection.executeUpdateDelete(SQL.DELETE, arrayOf(20, "Tom"))

// UPDATE
databaseConnection.executeUpdateDelete(SQL.UPDATE, arrayOf(20, "Tom"))

// SELECT
val cursor: CommonCursor = databaseConnection.query(SQL.QUERY, arrayOf(20, "Tom"))
cursor.forEachRows { index -> // Index of rows
    val age: Int = cursor.getInt("age")
    val name: String = cursor.getString("name")
}

// Create table and others
databaseConnection.execSQL(SQL.CREATE_TABLE)
```
You can bind some parameters to your SQL statement that using `Array<Any?>`. Totally, _sqllin-driver's_ usage is not difficult.