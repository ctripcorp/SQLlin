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

val sqllinVersion = "1.3.1"

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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.5.1")

                // Since 1.2.2, sqllin-dsl depends on kotlinx.coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
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

当前由于会改变数据库结构的操作暂时还没有 DSL 化支持。因此，你需要在 `create` 和 `update` 参数中使用字符串编写 SQL 语句。

通常你只需要在你的组件的生命周期内创建一个 `Database` 对象，所以你需要在组件的生命周期结束时手动关闭数据库：

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
上添加 `@Serializable` 注解。

## 接下来

你已经学习完了所有的准备工作，现在可以开始学习如何操作数据库了：

- [修改数据库与事务](modify-database-and-transaction-cn.md)
- [查询](query-cn.md)
- [并发安全](concurrency-safety-cn.md)
- [SQL 函数](sql-functions-cn.md)
- [高级查询](advanced-query-cn.md)
