# 开始使用

**欢迎使用 SQLlin ！！！**

## 在 Gradle 中使用 Maven 引入

将 _sqllin-dsl_、_sqllin-driver_ 以及 _sqllin-processor_ 依赖添加到你的 `build.gradle.kts`：

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
> 注意：如果你想将 SQLlin 的依赖添加到你的 Kotlin/Native 可执行程序工程，有时你需要正确添加对 SQLite 的 `linkerOpts` 到你的
> `build.gradle.kts`。你可以参考 [issue #48](https://github.com/ctripcorp/SQLlin/issues/48) 来获取更多信息。

## 创建数据库

创建 `Database` 实例如下示例所示：

```kotlin
import com.ctrip.sqllin.dsl.Database

val database = Database(name = "Person.db", path = getGlobalPath(), version = 1)
```
`DatabasePath` 是 `Database` 构造函数中第二个参数的类型，它在不同的平台上的标识有所不同。在 Android
中，你可以通过[`Context`](https://developer.android.com/reference/android/content/Context) 来获取它，在 native 平台上则可以通过字符串来获取它。
比如，你可以在 common source set 中定义一个 expect 函数：

```kotlin
import com.ctrip.sqllin.driver.DatabasePath

expect fun getGlobalDatabasePath(): DatabasePath
```
在 Android source set 中，你可以这样实现它：

```kotlin
import android.content.Context
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.driver.toDatabasePath

actual fun getGlobalDatabasePath(): DatabasePath =
    applicationContext.toDatabasePath() 
    
val applicationContext: Context
    get() {
        // 使用自己的方式获取 applicationContext
    }
```

在 iOS source set 中（在其他 Apple 平台也类似），可以这样实现：

```kotlin
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.driver.toDatabasePath

actual fun getGlobalDatabasePath(): DatabasePath =
    (NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory, 
        NSUserDomainMask, true).firstOrNull() as? String ?: ""
    ).toDatabasePath()

```
你也可以在创建 `Database` 实例的时候配置更多的 SQLite 参数：

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
注意，由于 Android Framework 的限制，`inMemory`、`journalMode`、`lookasideSlotSize`、`lookasideSlotCount` 这些参数仅在 Android 9 及以上版本生效。 并且，由于
[sqlite-jdbc](https://github.com/xerial/sqlite-jdbc)（SQLlin 在 JVM 上基于它）不支持 `sqlite3_config()`，`lookasideSlotSize` 和 `lookasideSlotCount` 两个属性在 JVM 平台不生效。

### 使用 DSLDBConfiguration 进行类型安全的模式管理

除此之外，你还可以使用新的试验性 API `DSLDBConfiguration`，它允许你在 `create` 和 `upgrade` 回调中使用类型安全的 SQL DSL，而不是原始的 SQL 字符串：

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

通过使用 `DSLDBConfiguration`，你可以直接在回调中使用 CREATE、DROP 和 ALTER 操作，使模式管理更加类型安全和易于维护。这些回调中可用的 DSL 操作与常规 `database { }` 块中可用的操作相同。

通常你只需要在你的组件的生命周期内创建一个 `Database` 对象，所以你需要在组件的生命周期结束时手动关闭数据库：

> 注意: `DSLDBConfiguration` 处于实验性阶段，但当其稳定后会彻底取代 `DatabaseConfiguration`, 也就是说在未来版本中 _sqllin-dsl_ 将不再支持使用 `DatabaseConfiguration` 创建 `Database` 实例。

```kotlin
override fun onDestroy() {
    database.close()
}
```

## 定义你的数据库实体

在 _sqllin-dsl_ 中，你可以直接插入或查找对象。所以，你需要使用正确的方式定义你的数据库实体，比如：

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class Person(
    val name: String,
    val age: Int,
)
```
你定义的数据库实体的属性名应与数据库表的列名相对应。数据库实体不应该拥有名字与表中的所有列名均不相同的属性，但是
数据库实体的属性数量可以比表中列的数量少。

`@DBRow` 的参数 `tableName` 表示数据库中的表名，请确保传入正确的值。如果不手动传入，_sqllin-processor_
将会使用类名作为表名，比如 `Person` 类的默认表名是"Person"。

在 _sqllin-dsl_ 中，对象序列化为 SQL 语句，或者从游标中反序列化依赖 _kotlinx.serialization_，所以你需要在你的 data class
上添加 `@Serializable` 注解。因此，如果你想在序列化或反序列化以及 `Table` 类生成的时候忽略某些属性，你可以给你的属性添加 `kotlinx.serialization.Transient` 注解。

### 定义主键

SQLlin 提供了用于定义数据库表主键的注解。

#### 使用 @PrimaryKey 定义单一主键

使用 `@PrimaryKey` 标记单个属性作为主键：

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

**重要的类型和可空性规则：**

- **对于自增的 `Long` 主键**：属性**必须**声明为可空类型（`Long?`）。这会映射到 SQLite 的 `INTEGER PRIMARY KEY`，它作为内部 `rowid` 的别名。当插入 `id = null` 的新记录时，SQLite 会自动生成 ID。

- **对于其他类型（String、Int 等）**：属性**必须**是非空的。插入时必须提供唯一值：

```kotlin
@DBRow
@Serializable
data class User(
    @PrimaryKey
    val username: String,  // Non-nullable, user-provided primary key
    val email: String,
)
```

`autoIncrement` 参数启用更严格的自增行为（使用 `AUTOINCREMENT` 关键字），确保行 ID 永远不会被重用。这仅对 `Long?` 属性有意义。

#### 使用 @CompositePrimaryKey 定义组合主键

当表的主键由多个列组成时，使用 `@CompositePrimaryKey`：

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

**重要规则：**

- 你可以在同一个类中对**多个属性**应用 `@CompositePrimaryKey`
- 所有带有 `@CompositePrimaryKey` 的属性**必须是非空的**
- 你**不能**在同一个类中混合使用 `@PrimaryKey` 和 `@CompositePrimaryKey` - 只能使用其中一个
- 所有 `@CompositePrimaryKey` 属性的组合形成表的组合主键

## 接下来

你已经学习完了所有的准备工作，现在可以开始学习如何操作数据库了：

- [修改数据库与事务](modify-database-and-transaction-cn.md)
- [查询](query-cn.md)
- [并发安全](concurrency-safety-cn.md)
- [SQL 函数](sql-functions-cn.md)
- [高级查询](advanced-query-cn.md)
