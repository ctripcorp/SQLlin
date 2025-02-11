# sqllin-driver 基本设计与使用

## 设计

最初我们需要一个多平台可用的低阶 Kotlin API 来调用 SQLite。因为我们认为 _sqllin-dsl_ 应该是平台无关的。
所以我们需要 _sqllin-driver_ ，并且 _sqllin-dsl_ 要基于它。我们的目标是编写 Kotlin Multiplatform common
source set 可用的通用 API，并且它们在不同的平台有不同的实现。

在 Android 上，并没有太多的方法可供我们选择。如果我们使用 Android Framework SQLite Java
API，事情将会变得非常简单，但是缺点是很多 SQLite 参数不能在 Android P 以下版本的系统上生效。如果我们自己编写
JNI 代码去调用 SQLite C 函数，看起来可以解决这个问题，但是会遇到一个更大的问题：在版本高于 Android N
的系统上，Google 不允许开发者在 NDK 中调用系统内置的 SQLite C 函数。如果我们坚定地选择这条路，我们必须自己将
SQLite 源码编译到 _sqllin-driver_ ，这将让我们的工程变得更复杂。最后我们还是选择了基于 Android Framework Java API。

在 Native 平台上，事情看起来有所不同。我们可以直接调用 SQLite C API，这是一种最直观的方式。Kotlin/Native 与 C
语言交互的能力非常完善，但是在 Kotlin/Native 中，你必须使用一些非常难以理解的 API 来进行与 C 的互操作，比如：`memScoped`、
`CPointer`、`CPointerVarOf` 以及 `toKString` 等等。所以在最开始时，我选择了 [SQLiter](https://github.com/touchlab/SQLiter)，这是一个
Kotlin/Native 多平台库。如果我使用它，就可以将 Kotlin-C 互操作转化为 Kotlin 语言内部的互相调用，非常方便。[SQLiter](https://github.com/touchlab/SQLiter)
也是 [SQLDelight](https://github.com/cashapp/sqldelight) 在 native 平台上调用 SQLite C 库的驱动程序。它不仅仅支持 iOS，它也支持苹果的全系操作系统、Linux（x64）以及
Windows（mingwX86, mingwX64）。

但是在几个月后，我发现使用 [SQLiter](https://github.com/touchlab/SQLiter) 也有许多缺点。比如说，[SQLiter](https://github.com/touchlab/SQLiter) 的更新频率非常低。我提交了一个 PR
很长时间没有被合并，也没有人回复我。并且，在 Kotlin `1.8.0` 之后，Kotlin/Native 新增了一个目标平台：`watchosDeviceArm64`。由于 [SQLiter](https://github.com/touchlab/SQLiter)
更新缓慢，SQLlin 也同样无法支持 `watchosDeviceArm64`。所以我决定自己重新实现与 SQLite C API 的互操作，就像最开始构想的那样。在 `1.1.0` 版本之前，_sqllin-driver_ 使用
[SQLiter](https://github.com/touchlab/SQLiter)，而在 `1.1.0`（包含）之后 _sqllin-driver_ 则使用*新 Native 驱动*。

无论如何，[SQLiter](https://github.com/touchlab/SQLiter) 仍然是一个非常棒的项目。我参考了许多它的设计与实现细节并将它们用在了 _sqllin-driver_ 的*新 Native 驱动*中。

从 `1.2.0` 开始, SQLlin 开始支持 JVM 目标平台，基于 [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc)。

## 基本用法

我不建议你在应用程序工程中直接使用 _sqllin-driver_ ，但是如果你想开发自己的 SQLite 高阶 API 库，你可以选择使用它作为底层驱动。

### 在 Gradle 中通过 Maven 引入

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

### 打开和关闭数据库

```kotlin
// 打开 SQLite
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
// 关闭 SQLite
databaseConnection.close()
```

你可以在 `DatabaseConfiguration` 中配置很多 SQLite 参数，它们的含义如同它们的名字。

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

// 创建表以及其他语句
databaseConnection.execSQL(SQL.CREATE_TABLE)
```

你可以使用 `Array<Any?>` 在 SQL 语句中绑定一些参数。总的来说， _sqllin-driver_ 的用法并不难。