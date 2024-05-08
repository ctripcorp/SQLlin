# The sqllin-driver Basic Design and Usage

中文版请见[这里](README_CN.md)

## Design

Initially, we needed a multiplatform available low-level Kotlin APIs to call SQLite. Because we thought _sqllin-dsl_
should be platform-independent. So, we needed _sqllin-driver_, and _sqllin-dsl_ on top of it. Our goal was
writing the common APIs in Kotlin Multiplatform common source set and they have different implementations on
different platforms.

On Android, there are no many ways to choose from. If we use Android Framework SQLite Java APIs, everything will be simple,
but defect is many SQLite parameters cannot take effect on systems below Android P. If we write JNI code
to call SQLite C functions by ourselves, above problem will be resolved, but this will lead to a bigger problem:
On systems above Android N, Google doesn't allow developers call system built-in SQLite C functions in NDK. If
we firmly choose this plan, we have to compile the SQLite source code into _sqllin-driver_, this will complicate
our project. Finally, we still choose to use Android Framework Java API.

On Native platforms, things look different. We can call SQLite C APIs directly, this is a most intuitive plan.
The interoperability of Kotlin/Native with C is perfect, but in Kotlin/Native you must use some APIs to interop with C
that very difficult to understand, like: `memScoped`, `CPointer`, `CPointerVarOf`, `toKString`, etc..
So, at the beginning, I chose the [SQLiter](https://github.com/touchlab/SQLiter), it is a Kotlin/Native multiplatform
library. If I use it, I can put the Kotlin-C interop translate to Kotlin language-internal calling. It is very
convenient. [SQLiter](https://github.com/touchlab/SQLiter) also is the driver of [SQLDelight](https://github.com/cashapp/sqldelight) calling SQLite C library on native platforms. It not only
supports iOS, but also supports all the operating systems of Apple, Linux(x64) and Windows(mingwX86, mingwX64).

But a few months later. I found using [SQLiter](https://github.com/touchlab/SQLiter) also has some disadvantages. For
example, [SQLiter](https://github.com/touchlab/SQLiter) updates very infrequently. I submitted a PR too long time, but
it still hasn't been merged, and no one replied to me. And, after Kotlin `1.8.0`, Kotlin/Native added a new target:
`watchosDeviceArm64`. Due to [SQLiter](https://github.com/touchlab/SQLiter) updates infrequently, SQLlin can't support
`watchosDeviceArm64` either. So, I decided to implement interoping with SQLite C APIs by myself as I originally conceived.
Before the version `1.1.0`, _sqllin-driver_ use [SQLiter](https://github.com/touchlab/SQLiter), and after `1.1.0`(including),
_sqllin-driver_ use the _New Native Driver_.

Whatever, [SQLiter](https://github.com/touchlab/SQLiter) still is a good project. I referred to a lot of designs and code
details from it and use them in _New Native Driver_ in _sqllin-driver_ .

Since `1.2.0`, SQLlin started to support JVM target, and it's base on [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc).

## Basic usage

I don't recommend you use _sqllin-driver_ in your application projects directly, but if you want to develop your own SQLite
high-level API library, you can use it as your underlying driver.

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
cursor.forEachRow { index -> // Index of rows
    val age: Int = cursor.getInt("age")
    val name: String = cursor.getString("name")
}

// Create table and others
databaseConnection.execSQL(SQL.CREATE_TABLE)
```
You can bind some parameters to your SQL statement that using `Array<Any?>`. Totally, _sqllin-driver's_ usage is not difficult.