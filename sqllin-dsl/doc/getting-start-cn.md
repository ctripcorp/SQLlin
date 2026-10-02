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
        // Use your own way to get `applicationContext`
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
数据库实体的属性数量可以比表中列的数量少（当且仅当你不需要使用 _sqllin-dsl_ 来创建表的情况下）。

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

### 列约束和修饰符

SQLlin 提供了多个注解来为表列添加约束和修饰符。

#### @Unique - 单列唯一性

使用 `@Unique` 强制要求列中的值不能重复：

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

**重要注意事项：**
- UNIQUE 列允许多个 NULL 值（在 SQL 中 NULL 不等于 NULL）
- 要防止 NULL 值，请使用非空类型：`val email: String`

#### @CompositeUnique - 多列唯一性

使用 `@CompositeUnique` 确保**多个列的组合**是唯一的：

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

**分组：** 属性可以通过指定不同的组号属于多个唯一约束组：

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

**默认行为：**
- 如果未指定组：`@CompositeUnique()`，默认为组 `0`
- 所有具有相同组号的属性会组合成一个组合约束

#### @CollateNoCase - 不区分大小写的文本比较

使用 `@CollateNoCase` 使字符串比较不区分大小写：

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.CollateNoCase
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

**类型限制：**
- **只能**应用于 `String` 或 `Char` 属性（及其可空变体）
- 尝试在非文本类型上使用会导致编译时错误

**COLLATE NOCASE 的 SQLite 行为：**
- `'ABC' = 'abc'` 结果为 true
- `ORDER BY` 子句不区分大小写排序
- 列上的索引不区分大小写

#### 组合多个约束

你可以在同一个属性上组合多个约束注解：

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

#### @Default - 列默认值

使用 `@Default` 为 CREATE TABLE 语句中的列指定默认值。当插入行时未显式提供这些列的值时，SQLite 会自动使用这些默认值：

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.Default
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class User(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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

**值格式：**
- **字符串**：必须用单引号括起来：`'默认文本'`
- **数字**：纯数字字面量：`0`、`42`、`3.14`
- **布尔值**：用 `0` 表示 false，用 `1` 表示 true
- **NULL**：使用字面量 `NULL`
- **表达式**：SQLite 函数，如 `CURRENT_TIMESTAMP`、`datetime('now')`、`(random())` 等

**与外键触发器的集成：**

当使用 `ON_DELETE_SET_DEFAULT` 或 `ON_UPDATE_SET_DEFAULT` 触发器时，**必须**设置默认值：

```kotlin
@DBRow
@Serializable
data class Order(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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

**重要注意事项：**
- **字符串值必须使用单引号**：`'text'`，而不是 `"text"`
- 默认值不会覆盖 INSERT 语句中显式提供的值
- 像 `CURRENT_TIMESTAMP` 这样的函数在插入时求值，而不是在创建表时
- 注解处理器不会验证默认值是否与列类型匹配

**常见陷阱：**

```kotlin
// ❌ Wrong - using double quotes for strings
@Default("\"active\"")
val status: String

// ✅ Correct - using single quotes for strings
@Default("'active'")
val status: String
```

### 支持的类型

SQLlin 支持以下 Kotlin 类型用于 `@DBRow` 数据类的属性：

#### 数值类型
- **整数类型：** `Byte`、`Short`、`Int`、`Long`
- **无符号整数类型：** `UByte`、`UShort`、`UInt`、`ULong`
- **浮点类型：** `Float`、`Double`

#### 文本类型
- `String` - 映射到 SQLite TEXT
- `Char` - 映射到 SQLite CHAR(1)

#### 其他类型
- `Boolean` - 映射到 SQLite BOOLEAN（存储为 0 或 1）
- `ByteArray` - 映射到 SQLite BLOB（用于二进制数据）
- **枚举类** - 映射到 SQLite INT（存储为序数值）

#### 类型别名
- 上述支持类型的任何类型别名
- 类型别名可以嵌套（一个类型别名的类型别名）

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

**重要注意事项：**
- 处理器会递归解析类型别名以找到底层类型
- 底层类型必须是上述支持的类型之一

#### 可空类型
- 上述所有类型都可以为可空（例如 `String?`、`Int?`、`Boolean?`）
- 例外：主键有特殊的可空性规则（参见主键部分）

#### 枚举示例

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

**重要注意事项：**
- 枚举值存储为其序数（整数）值
- 更改枚举常量的顺序会影响存储的值
- 如果需要更稳定的存储，考虑使用 String

#### SQLite 类型映射

| Kotlin 类型 | SQLite 类型 |
|------------|-------------|
| Byte, UByte | TINYINT |
| Short, UShort | SMALLINT |
| Int, UInt | INT |
| Long | BIGINT（如果是主键则为 INTEGER） |
| ULong | BIGINT |
| Float | FLOAT |
| Double | DOUBLE |
| Boolean | BOOLEAN |
| Char | CHAR(1) |
| String | TEXT |
| ByteArray | BLOB |
| Enum | INT |

### 外键约束

SQLlin 提供了对外键约束的全面支持,以维护表之间的引用完整性。外键通过在插入、更新或删除数据时强制执行规则,确保表之间的关系保持一致。

#### 重要：启用外键

默认情况下,SQLite **不会强制执行**外键约束(为了向后兼容)。你必须在创建表之前使用 `PRAGMA_FOREIGN_KEYS(true)` 显式启用外键强制执行：

```kotlin
database {
    // CRITICAL: Enable foreign key enforcement first
    PRAGMA_FOREIGN_KEYS(true)

    // Now create tables with foreign keys
    CREATE(UserTable)
    CREATE(OrderTable)  // Has foreign key to UserTable
}
```

**关键点：**
- 此设置是**每个连接**的,必须在每次打开数据库时设置
- 此设置**不能**在事务内部更改
- 如果不启用此设置,外键将成为模式的一部分但**不会被强制执行**

#### 定义外键

SQLlin 提供了两种定义外键的方法：

##### 方法 1：使用 @References 的列级外键

对于简单的单列外键,使用 `@References`。这是**大多数用例的推荐方法**：

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.References
import com.ctrip.sqllin.dsl.annotation.Trigger
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class User(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    val name: String,
    val email: String,
)

@DBRow
@Serializable
data class Order(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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

##### 方法 2：使用 @ForeignKeyGroup + @ForeignKey 的表级外键

对于引用多个列的组合外键,使用此方法：

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
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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

#### 引用操作（触发器）

触发器定义了当被引用的行被删除或更新时会发生什么。SQLlin 通过 `Trigger` 枚举支持所有标准 SQLite 触发器：

##### DELETE 触发器

**ON_DELETE_CASCADE**：当父行被删除时,自动删除子行
```kotlin
@DBRow
@Serializable
data class Order(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_CASCADE)
    val userId: Long,
    val amount: Double,
)
// When a User is deleted, all their Orders are automatically deleted
```

**ON_DELETE_SET_NULL**：当父行被删除时,将外键设置为 NULL（需要可空列）
```kotlin
@DBRow
@Serializable
data class Post(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_SET_NULL)
    val authorId: Long?,  // Must be nullable!
    val content: String,
)
// When a User is deleted, their Posts remain but authorId becomes NULL
```

**ON_DELETE_RESTRICT**：如果存在子行,阻止删除父行
```kotlin
@DBRow
@Serializable
data class OrderItem(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    @References(tableName = "Order", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_RESTRICT)
    val orderId: Long,
    val productId: Long,
)
// An Order cannot be deleted if it has OrderItems
```

**ON_DELETE_SET_DEFAULT**：当父行被删除时,将外键设置为其默认值
```kotlin
@DBRow
@Serializable
data class Comment(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_SET_DEFAULT)
    val userId: Long = 0L,  // Default to 0 (anonymous user)
    val content: String,
)
```

##### UPDATE 触发器

UPDATE 操作也有相同的操作：
- `ON_UPDATE_CASCADE`：当父主键更改时,更新子外键
- `ON_UPDATE_SET_NULL`：将子外键设置为 NULL（需要可空列）
- `ON_UPDATE_RESTRICT`：如果存在子行,阻止更新父主键
- `ON_UPDATE_SET_DEFAULT`：将子外键设置为默认值

##### 触发器行为摘要

| 触发器 | 父行删除/更新 | 子行行为 | 需要可空？ |
|---------|------------------------|----------------|-------------------|
| NULL（默认） | 允许 | 无变化 | 否 |
| CASCADE | 允许 | 子行被删除/更新 | 否 |
| SET_NULL | 允许 | 外键设置为 NULL | **是** |
| SET_DEFAULT | 允许 | 外键设置为 DEFAULT | 否 |
| RESTRICT | **阻止** | 操作失败 | 否 |

#### 多个外键

一个表可以有多个指向不同父表的外键约束：

```kotlin
@DBRow
@Serializable
@ForeignKeyGroup(group = 0, tableName = "User", trigger = Trigger.ON_DELETE_CASCADE)
@ForeignKeyGroup(group = 1, tableName = "Product", trigger = Trigger.ON_DELETE_RESTRICT)
data class OrderItem(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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

或使用 `@References`：
```kotlin
@DBRow
@Serializable
data class OrderItem(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_CASCADE)
    val userId: Long,
    @References(tableName = "Product", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_RESTRICT)
    val productId: Long,
    val quantity: Int,
)
```

#### 命名约束

你可以选择为外键约束命名,以获得更好的错误消息和模式内省：

```kotlin
@DBRow
@Serializable
data class Order(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    @References(
        tableName = "User",
        foreignKeys = ["id"],
        trigger = Trigger.ON_DELETE_CASCADE,
        constraintName = "fk_order_user"  // 可选的约束名称
    )
    val userId: Long,
)
// Generated SQL: userId BIGINT CONSTRAINT fk_order_user REFERENCES User(id) ON DELETE CASCADE
```

#### 最佳实践

1. **始终启用外键**：在每个数据库会话开始时调用 `PRAGMA_FOREIGN_KEYS(true)`
2. **先创建父表**：在创建具有外键的表之前创建被引用的表
3. **对依赖数据使用 CASCADE**：当子数据不应该在没有父数据的情况下存在时使用 `ON_DELETE_CASCADE`
4. **对可选关系使用 SET_NULL**：当子数据可以独立存在时使用 `ON_DELETE_SET_NULL`
5. **使用 RESTRICT 进行保护**：使用 `ON_DELETE_RESTRICT` 防止意外删除父数据
6. **考虑可空列**：当关系是可选的时使用可空的外键列
7. **命名你的约束**：使用 `constraintName` 参数以获得更好的调试和错误消息

#### 完整示例

这是一个演示外键关系的完整示例：

```kotlin
import com.ctrip.sqllin.dsl.Database
import com.ctrip.sqllin.dsl.annotation.*
import kotlinx.serialization.Serializable

// Parent table: Users
@DBRow
@Serializable
data class User(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    @Unique val email: String,
    val name: String,
)

// Child table: Orders with CASCADE delete
@DBRow
@Serializable
data class Order(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_CASCADE)
    val userId: Long,
    val amount: Double,
    val orderDate: String,
)

// Child table: Posts with SET_NULL delete
@DBRow
@Serializable
data class Post(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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

## 接下来

你已经学习完了所有的准备工作，现在可以开始学习如何操作数据库了：

- [修改数据库与事务](modify-database-and-transaction-cn.md)
- [查询](query-cn.md)
- [并发安全](concurrency-safety-cn.md)
- [SQL 函数](sql-functions-cn.md)
- [高级查询](advanced-query-cn.md)
