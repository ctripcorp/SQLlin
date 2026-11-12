# 测试

使用 SQLlin 的代码，应当像 SQLlin 自己的测试那样，针对真实的 SQLite 数据库来测试，而不是针对保存在内存里的假实现。假实现的行为取决于
它是怎么写的，而不是 SQLite 实际怎么做：比如它按插入顺序返回行，而对于没有 _ORDER BY_ 的查询，SQLite 按它读取的索引的顺序返回行。
本文介绍各个平台上真实数据库运行在哪里、这样的测试能说明什么、不能说明什么，以及如何测试迁移。

## 真实数据库运行在哪里

| 测试 | 运行的 SQLite | 需要什么 |
|------|--------------|---------|
| JVM | [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) 自带的 | 不需要额外配置 |
| Android，在主机上 | Robolectric 的，运行在 Android 真实的框架代码之下 | 下文的配置 |
| macOS 和 Apple 的模拟器 | 系统自带的 | 不需要额外配置 |
| Linux 和 Windows | 测试二进制链接的那一个 | 一个可供链接的 `libsqlite3`，见[开始使用](getting-start-cn.md)中关于 `linkerOpts` 的说明 |

JVM 测试可以用任意目录作为数据库的路径：

```kotlin
private val path = Files.createTempDirectory("database").toString().toDatabasePath()
```

测试只写一份：放在 `commonTest` 的一个类里，这个类接收 `DatabasePath`；再由每个平台的测试类调用它，平台的测试类只负责取得路径。
SQLlin 自己的测试就是这样写的。

### 用 Robolectric 做 Android 主机测试

[Robolectric](https://robolectric.org) 在 JVM 上运行 Android 的框架，所以 SQLlin 的 Android 驱动会像在设备上一样运行，不需要
模拟器。使用 Android Kotlin Multiplatform library 插件时，这些测试放在 `androidHostTest` 源集里：

```kotlin
kotlin {
    android {
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        getByName("androidHostTest").dependencies {
            implementation("junit:junit:$junitVersion")
            implementation("androidx.test:core:$androidxTestVersion")
            implementation("org.robolectric:robolectric:$robolectricVersion")
        }
    }
}

// 对较新的 SDK 级别，Robolectric 会反射访问 JDK 的内部实现
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
```

在 JDK 21 上使用 Robolectric 4.17 时，如果没有这个 JVM 参数，SDK 36 和 37 的测试会失败，报错为 `Failed to interact with raw
FileDescriptor internals`，而 SDK 34 及以下的测试可以通过。

Robolectric 的 runner 是 JUnit 4 的，所以测试类必须放在 `androidHostTest` 里，不能放在 `commonTest` 里：

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 37])
class MovieDatabaseAndroidTest {

    private val tests = MovieDatabaseTests(ApplicationProvider.getApplicationContext<Context>().toDatabasePath())

    @Test
    fun testInsert() = tests.testInsert()
}
```

### SDK 级别决定什么

**`@Config` 的 SDK 级别决定的是运行哪一套框架代码，而不是 SQLite 的版本。** 两个级别就能覆盖 SQLlin 的 Android 驱动在框架里走的
路径：一个低于 28，因为从 API 28 起数据库通过 `SQLiteDatabase.OpenParams` 打开；一个不低于 33，从这里起框架通过多个连接读取 WAL
模式的数据库。每多一个级别，所有测试都会再跑一遍，所以只给会打开数据库的测试配置多个级别。

SQLite 则是 Robolectric 原生运行时里的那一个。使用 Robolectric 4.17 时，SDK 34 及以下的每个级别都是 3.32.2，36 是 3.44.3，
37 是 3.50.6；而 API 24 的设备上是 SQLite 3.9，API 34 的设备上是 3.39。所以在主机测试里：

- 一条需要比设备更新的 SQLite 的语句，在 `sdk = 24` 下能通过，在那台设备上却会失败
- 一个在 API 34 设备上能用的 API，比如需要 SQLite 3.35 的 `DROP_COLUMN`，在 `sdk = 34` 下却会失败

这些版本会随 Robolectric 的版本变化，所以不要依赖上面的数字，而是直接问 SQLite 它是哪个版本：

```kotlin
val version = SQLiteDatabase.create(null).use { database ->
    database.rawQuery("select sqlite_version()", null).use { it.moveToFirst(); it.getString(0) }
}
```

依赖 SQLite 版本的功能，比如标有 `@PlatformDependentSQLiteAPI` 的 API，要在对应 API 级别的模拟器或设备上测试。

## 测试用的数据库

**让路径保持为参数。** 创建 App 的 `Database` 的函数应当接收 `DatabasePath`，并以 App 自己的路径作为默认值。这样测试可以传入自己的
路径，而不必依赖 App 取得 `Context` 的方式：

```kotlin
fun createMovieDatabase(path: DatabasePath = databasePath): Database =
    Database(DSLDBConfiguration(name = "movie.db", path = path, version = 3, create = { /* ... */ }))
```

**删除数据库之前先关闭它。** sqllin-driver 的 `deleteDatabase(path, name)` 会删除数据库文件以及它的 `-wal`、`-shm` 和
`-journal` 文件。Windows 无法删除打开着的文件，而仍然打开的数据库会一直占着它的文件：

```kotlin
@AfterTest
fun tearDown() {
    database.close()
    deleteDatabase(path, "movie.db")
}
```

在 JVM 和原生平台的测试里，一个测试留下的文件到下一个测试时还在，所以要在每个测试之前和之后都删除它。Robolectric 给每个测试一个
独立的数据目录，主机测试不会遇到别的测试留下的文件，只需要关闭数据库。

## 协程

`observe { }` 和 sqllin-dsl-paging 的 `pagingSource { }` 默认在 `Dispatchers.IO` 中执行查询，这在 `runTest` 下可以直接使用：
查询是测试挂起等待的真实工作，而不是需要虚拟时间跳过的延迟。SQLlin 自己的测试就是这样加载 `pagingSource` 的，既通过
androidx.paging 的 `TestPager`，也通过 `Pager` 和 `asSnapshot { }`。

要断言一个可观察查询**没有**发出任何结果，就让它的查询在测试自己的上下文里执行，这样 `runCurrent()` 能让它们执行完：

```kotlin
@Test
fun doesNotEmitForAnotherTable() = runTest {
    val books = Channel<List<Book>>(Channel.UNLIMITED)
    backgroundScope.launch {
        database.observe(EmptyCoroutineContext) { BookTable SELECT X }.collect { books.send(it) }
    }
    assertEquals(emptyList(), books.receive())

    database suspendedScope { PersonTable INSERT Person(name = "Ann", age = 30) }
    runCurrent()
    assertTrue(books.tryReceive().isFailure)
}
```

## 只有测试才有的类的表

[开始使用](getting-start-cn.md)中的配置只对 `commonMain` 运行 sqllin-processor，所以测试源集里的 `@DBRow` 类不会得到表对象，
比如为了测试迁移而保留旧结构的类。给需要它的测试编译加上 processor：

```kotlin
dependencies {
    add("kspCommonMainMetadata", "com.ctrip.kotlin:sqllin-processor:$sqllinVersion")
    add("kspAndroidHostTest", "com.ctrip.kotlin:sqllin-processor:$sqllinVersion")
    add("kspJvmTest", "com.ctrip.kotlin:sqllin-processor:$sqllinVersion")
}
```

原生目标也有这样的配置，比如 `kspIosSimulatorArm64Test`。它只为这个编译的测试源集里的类生成表对象，`commonMain` 里的类仍然由
`kspCommonMainMetadata` 负责。

类可以放在 `commonTest` 里，也可以放在平台的测试源集里，但**使用它的表对象的代码必须放在平台的测试源集里**，比如
`androidHostTest` 或 `jvmTest`：表对象属于平台的编译，`commonTest` 的代码看不到它，会报 `Unresolved reference`。

## 测试迁移

用旧版本创建数据库，关闭它，再用 App 自己的配置重新打开，然后检查升级留下了什么：

```kotlin
// 放在 androidHostTest 或 jvmTest 里：版本 2 的结构，版本 3 会删除其中一张表
@DBRow("movie_genres")
@Serializable
data class MovieGenreV2(val movieId: Long, val genreId: Int)

@OptIn(ExperimentalDSLDatabaseAPI::class)
@Test
fun upgradesFrom2To3() {
    // 1. 由测试创建的版本 2
    Database(
        DSLDBConfiguration(
            name = "movie.db",
            path = path,
            version = 2,
            create = {
                CREATE(MovieTable)
                CREATE(MovieGenreV2Table)
            },
        )
    ).close()

    // 2. App 自己的配置，版本 3，它的 `upgrade` 在打开数据库时执行
    val database = createMovieDatabase(path)
    try {
        // 3. 升级留下了什么
        lateinit var dropped: PragmaStatement<List<ColumnInfo>>
        database {
            dropped = PRAGMA_TABLE_INFO("movie_genres")
        }
        assertTrue(dropped.getResults().isEmpty())
    } finally {
        database.close()
    }
}
```

- **旧结构**是测试里的一个 `@DBRow` 类，使用这张表的名字，它的表对象按上一节所说由 processor 生成。如果只关心表的名字，可以用另一张
  表的 `withName(name)` 来代替
- **`PRAGMA_TABLE_INFO(name)`** 返回一张表的列，表不存在时返回空列表，所以它既能检查迁移创建了什么，也能检查迁移删除了什么。要检查
  迁移保留了什么，就在旧版本下插入行，升级后再查询出来
- **确认测试能够失败**：去掉它所测试的那一步迁移，看它是否失败。如果旧结构根本没有创建出来，迁移测试会毫无意义地通过：对于测试
  从没创建过的表，`PRAGMA_TABLE_INFO` 同样返回空列表，和被升级删除的表一样
