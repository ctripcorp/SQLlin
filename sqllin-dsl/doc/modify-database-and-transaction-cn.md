# 修改数据库与事务

在[《开始使用》](getting-start-cn.md)中，我们学习了如何创建 `Database` 实例以及定义你自己的数据库实体。现在我们将开始学习如何在 SQLlin 中编写 SQL 语句。

## 表结构操作

SQLlin 提供了用于管理表结构的类型安全 DSL 操作：CREATE、DROP 和 ALTER，以及视图和索引的操作。

### CREATE - 创建表

你可以使用 CREATE 操作直接从数据类定义创建表：

```kotlin
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import kotlinx.serialization.Serializable

@DBRow
@Serializable
data class Person(
    @PrimaryKey(autoIncrement = true)
    val id: Long = 0,
    val name: String,
    val age: Int,
)

fun sample() {
    database {
        // Create table using infix notation
        CREATE(PersonTable)

        // Or using extension function
        PersonTable.CREATE()
    }
}
```

CREATE 操作会根据你的数据类定义自动生成相应的 SQL CREATE TABLE 语句，包括：
- 正确的列类型（String → TEXT、Int → INT、Long → INTEGER/BIGINT 等）
- 非空属性的 NOT NULL 约束
- PRIMARY KEY 约束（单一或组合主键）
- 自增主键的 AUTOINCREMENT

### DROP - 删除表

DROP 操作会从数据库中永久删除表及其所有数据：

```kotlin
fun sample() {
    database {
        // Drop table using infix notation
        DROP(PersonTable)

        // Or using extension function
        PersonTable.DROP()
    }
}
```

**⚠️ 警告**：DROP 是一个破坏性操作。执行后，表及其所有数据将被永久删除。请谨慎使用。

### CREATE VIEW - 创建视图

视图是保存在数据库里的一条 _SELECT_，查询时可以像表一样读取它。用 `@DBView` 声明视图的行，就像用 `@DBRow` 声明表的行一样，
_sqllin-processor_ 会生成一个以类名加 `View` 后缀命名的视图对象。视图是实验性 API，使用时需要 `@OptIn(ExperimentalDSLDatabaseAPI::class)`：

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBView("adults")
@Serializable
data class Adult(
    val name: String,
    val age: Int,
)

@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        // CREATE VIEW adults(name,age) AS SELECT name,age FROM person WHERE person.age>=18
        CREATE_VIEW(AdultView) AS (PersonTable SELECT WHERE<Adult>(PersonTable.age GTE 18))
    }
    lateinit var olderAdults: SelectStatement<Adult>
    database {
        AdultView { view ->
            olderAdults = view SELECT WHERE(age GT 60)
        }
    }
}
```

`AS` 后面的 _SELECT_ 读出的必须是视图的行类型，这一点在编译期检查。它可以是任何 _SELECT_：投影、结果列、Join 或组合查询都可以。
SQLite 不允许视图带参数，所以 SQLlin 会把 _SELECT_ 中的值直接写进视图的 SQL。

查询视图的方式和查询表一样，视图对象的属性就是它的列，也可以用在 Join 和组合查询中，`Database#observe` 会观察它读取的表。
视图不能写入：它的对象不是 `Table`，所以对它使用 `INSERT`、`UPDATE`、`DELETE`、`CREATE` 和 `ALTER` 都无法编译，它的属性也不能
加约束注解。`DROP(AdultView)` 会删除视图。视图的定义属于数据库结构的一部分：要修改它，就在 `upgrade` 中先删除、再重新创建。

### CREATE INDEX - 创建索引

索引能让按其列查找行的查询变快。`CREATE_INDEX` 在表的列上创建索引，`CREATE_UNIQUE_INDEX` 创建的索引还会保证任意两行在这些列上的值
不相同：

```kotlin
fun sample() {
    database {
        // CREATE INDEX idx_person_name ON person(name)
        PersonTable.CREATE_INDEX("idx_person_name", PersonTable.name)
        // CREATE UNIQUE INDEX idx_person_name_age ON person(name,age)
        PersonTable.CREATE_UNIQUE_INDEX("idx_person_name_age", PersonTable.name, PersonTable.age)
        // CREATE INDEX idx_person_lower_name ON person(lower(name))
        PersonTable.CREATE_INDEX("idx_person_lower_name", PersonTable.lower(PersonTable.name))
    }
}
```

用表的列的函数可以创建表达式索引，比较同一个表达式的查询会用到它，例如用 `lower(name)` 做不区分大小写的查找。索引只能包含它自己
所在表的列。

在 `CREATE_INDEX` 或 `CREATE_UNIQUE_INDEX` 后面加上 `WHERE` 就是部分索引：只索引满足条件的行。它比索引所有行的索引更小，唯一的部分
索引只保证这些行之间的值不相同，例如未删除用户的邮箱。部分索引是实验性 API：

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        // CREATE UNIQUE INDEX idx_user_email ON user(email) WHERE isDeleted<=0
        UserTable.CREATE_UNIQUE_INDEX("idx_user_email", UserTable.email) WHERE (UserTable.isDeleted IS false)
    }
}
```

SQLite 不允许索引的条件中带参数，也不允许子查询，所以 SQLlin 会把条件中的值直接写进 SQL，带子查询的条件会抛出
`IllegalArgumentException`。只有当查询的 _WHERE_ 能推出索引的条件时，查询才会用到部分索引。

`DROP_INDEX` 按名字删除索引，它是实验性 API：

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        DROP_INDEX("idx_person_name")
    }
}
```

### IF NOT EXISTS 与 IF EXISTS

表已存在时 `CREATE` 会失败，表不存在时 `DROP` 会失败。它们的 `IF NOT EXISTS` 和 `IF EXISTS` 版本则什么也不做，适用于表、视图和索引。
它们是实验性 API：

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        // CREATE TABLE IF NOT EXISTS person(...)
        CREATE_IF_NOT_EXISTS(PersonTable)  // 或 PersonTable.CREATE_IF_NOT_EXISTS()
        PersonTable.CREATE_INDEX_IF_NOT_EXISTS("idx_person_name", PersonTable.name)
        PersonTable.CREATE_UNIQUE_INDEX_IF_NOT_EXISTS("idx_person_name_age", PersonTable.name, PersonTable.age)
        CREATE_VIEW_IF_NOT_EXISTS(AdultView) AS (PersonTable SELECT WHERE<Adult>(PersonTable.age GTE 18))

        DROP_IF_EXISTS(AdultView)  // 或 AdultView.DROP_IF_EXISTS()
        DROP_INDEX_IF_EXISTS("idx_person_name")
        DROP_IF_EXISTS(PersonTable)  // 或 PersonTable.DROP_IF_EXISTS()
    }
}
```

`IF NOT EXISTS` 只检查名字：如果同名的表已经存在但列不同，它会保持原样。

### ALTER - 修改表结构

SQLlin 提供了多种 ALTER 操作来修改现有的表结构：

#### 添加列

向现有表添加新列：

```kotlin
@DBRow
@Serializable
data class Person(
    val name: String,
    val age: Int,
    val email: String? = null,  // New column
)

fun sample() {
    database {
        PersonTable ALTER_ADD_COLUMN PersonTable.email
    }
}
```

#### 重命名表

将现有表重命名为新名称：

```kotlin
fun sample() {
    database {
        // Rename using Table object
        PersonTable ALTER_RENAME_TABLE_TO NewPersonTable

        // Or rename using old table name as String
        "old_person" ALTER_RENAME_TABLE_TO NewPersonTable
    }
}
```

#### 依赖 SQLite 版本的 API

有些 API 需要某个版本的 SQLite，或者 SQLite 的某个编译选项，而有的平台没有。SQLlin 运行在哪个 SQLite 上取决于平台：Android 系统的
SQLite 在 API 24（SQLlin 的最低版本）上是 3.9，API 34 以下是 3.32，API 34 起是 3.39，并且没有数学函数等编译选项；Apple 系统的 SQLite
取决于系统版本；在 Linux 和 Windows 上，则是你的应用链接的 SQLite。这样的 API 在缺少所需条件的 SQLite 上会在运行时失败，所以它们都
标有 `@PlatformDependentSQLiteAPI`：不用 `@OptIn(PlatformDependentSQLiteAPI::class)` 声明同意就会编译报错。请只在你的应用的所有平台
都满足 API 文档中写明的条件时才这样做。

#### 重命名列

重命名表中的列。`ALTER TABLE ... RENAME COLUMN` 需要 SQLite 3.25.0，Android 从 API 30 起才具备：

```kotlin
@OptIn(PlatformDependentSQLiteAPI::class)
fun sample() {
    database {
        // Using ClauseElement references (type-safe)
        PersonTable.RENAME_COLUMN(PersonTable.age, PersonTable.yearsOld)

        // Or using String for old column name
        PersonTable.RENAME_COLUMN("age", PersonTable.yearsOld)
    }
}
```

#### 删除列

从现有表中删除列。`ALTER TABLE ... DROP COLUMN` 需要 SQLite 3.35.0：

```kotlin
@OptIn(PlatformDependentSQLiteAPI::class)
fun sample() {
    database {
        PersonTable DROP_COLUMN PersonTable.email
    }
}
```

**⚠️ 警告**：DROP COLUMN 会永久删除列及其所有数据。请注意，SQLite 的 DROP COLUMN 支持是在 3.35.0 版本中添加的，Android 要到 API 34 才具备，因此较旧的 SQLite 版本需要[重建表](#重建表)。

### 在 DSLDBConfiguration 中使用结构操作

这些操作在使用 `DSLDBConfiguration` 时的数据库创建和升级回调中特别有用：

```kotlin
import com.ctrip.sqllin.dsl.DSLDBConfiguration

val database = Database(
    DSLDBConfiguration(
        name = "Person.db",
        path = getGlobalDatabasePath(),
        version = 2,
        create = {
            CREATE(PersonTable)
            CREATE(AddressTable)
        },
        upgrade = { oldVersion, newVersion ->
            when (oldVersion) {
                1 -> {
                    // Upgrade from version 1 to 2
                    PersonTable ALTER_ADD_COLUMN PersonTable.email
                    CREATE(AddressTable)
                }
            }
        }
    )
)
```

### 重建表

SQLite 的 `ALTER TABLE` 只能重命名表，以及添加、重命名、删除列。其他改动，比如添加约束、把列改成 `NOT NULL`、更换主键，
都需要重建表：用临时名创建新结构的表，用 `INSERT INTO ... SELECT` 把数据拷过去，删除旧表，再把新表改名。在 SQLite 低于
3.35 的环境中删除列也是这样做，在 Android 上就是 API 34 以下。

要从旧表读取数据，需要保留一个描述旧结构的 `@DBRow` 类，表名不变。`withName` 用来给新结构起一个临时名：

```kotlin
import com.ctrip.sqllin.dsl.sql.withName

@DBRow("person")
@Serializable
data class PersonV1(        // 版本 1 中 'person' 的结构
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val name: String,
    val legacy: Int,
)

@DBRow("person")
@Serializable
data class Person(          // 版本 2 起的结构
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @Unique val fullName: String,
)

val database = Database(
    DSLDBConfiguration(
        // ...
        version = 2,
        upgrade = { oldVersion, newVersion ->
            if (oldVersion < 2) {
                val newPerson = PersonTable.withName("person_new")
                CREATE(newPerson)
                PersonV1Table { table ->
                    // INSERT INTO person_new(id,fullName) SELECT id,person.name AS fullName FROM person
                    newPerson INSERT (table SELECT (name AS Person::fullName))
                }
                DROP(PersonV1Table)
                "person_new" ALTER_RENAME_TABLE_TO PersonTable
            }
        }
    )
)
```

要重命名的是新表，而不是旧表。在 SQLite 的默认设置下，重命名一张表时，其他表外键中对它的引用也会跟着改名，所以如果先重命名
旧表，这些引用就会跟着旧表走，等旧表被删除后，它们就指向了一张不存在的表。索引会随旧表一起删除，所以要在重建后的表上重新创建。

`withName` 返回的表与原表有相同的列、约束和行类型，但没有列属性，因为那些属性指向的是原表。它用于针对整张表的语句：
`CREATE`、`INSERT`、`DROP` 和 `ALTER_RENAME_TABLE_TO`。
对于 [FTS 表](advanced-query-cn.md#全文搜索)，它返回的是选项相同的 FTS 表，所以要修改 FTS 表的列或选项，也是用同样的方法
重建。

数据的转换由 `SELECT` 完成，可以使用投影或结果列，详见[《高级查询》](advanced-query-cn.md)。转换值类型、替换 `NULL` 的函数目前还不支持。

## 插入

`Database` 类重载了类型为 `<T> Database.(Database.() -> T) -> T` 的函数操作符。当你调用该操作符函数时，它将产生一个 _DatabaseScope_ （数据库作用域）。
没错，它是该操作符函数的 lambda 表达式参数。任何 SQL 语句都必须写在 _DatabaseScope_ 内。并且当 _DatabaseScope_ 结束的时候内部的 SQL 语句才会执行。

你已经知道， _INSERT_、_DELETE_、_UPDATE_ 以及 _SELECT_ SQL 语句用于操作表。所以在你编写你的 SQL 语句之前，你还需要获取一个 `Table` 实例，就像这样：

```kotlin
private val database = Database(name = "Person.db", path = getGlobalPath(), version = 1)

fun sample() {
    database {
        PersonTable { table ->
            // Write your SQL statements...
        }
    }
}
```
`PersonTable` 由 _sqllin-processor_ 生成，这是因为 `Person` 类被添加了 `@DBRow` 注解。任何添加了 `@DBRow`
注解的类都会生成一个 `Table` object，它的名字为 `类名 + 'Table'`。

现在让我们来进行真正的 _INSERT_ 操作：

```kotlin
fun sample() {
    database {
        PersonTable { table ->
            table INSERT Person(age = 4, name = "Tom")
            table INSERT listOf(
                Person(age = 10, name = "Nick"),
                Person(age = 3, name = "Jerry"),
                Person(age = 8, name = "Jack"),
            )
        }
    }
}
```

_INSERT_ 语句可以直接插入对象，你可以一次插入一个或多个对象。

`INSERT_OR_IGNORE` 会跳过与表中已有行冲突的对象，`INSERT_OR_REPLACE` 则会替换那一行。它们接受的参数与 `INSERT` 相同。

_INSERT_ 还可以插入一条 _SELECT_ 返回的行，就像 `INSERT INTO ... SELECT` 那样，前提是这条 _SELECT_ 读出的是这张表的行类型。
这些行可以来自任意一张表，借助投影或结果列：

```kotlin
fun sample() {
    database {
        // INSERT INTO person_archive(id,name,age) SELECT id,name,age FROM person WHERE age > ?
        PersonArchiveTable INSERT (PersonTable SELECT WHERE<PersonArchive>(PersonTable.age GT 60))
    }
}
```

这条 _SELECT_ 会成为 _INSERT_ 的一部分，所以它不再单独执行，也无法读取它的结果。主键按查询出的值原样拷贝。
`INSERT_OR_IGNORE` 和 `INSERT_OR_REPLACE` 同样可以接受 _SELECT_。

## 删除

_DELETE_ 语句将会比 _INSERT_ 语句稍微复杂。SQLlin 不像 [Jetpack Room](https://developer.android.com/training/data-storage/room)
一样直接删除对象，而是使用 _WHERE_ 子句：

```kotlin
fun sample() {
    database {
        PersonTable { table ->
            table DELETE WHERE(age GTE 10 OR (name NEQ "Jerry"))
        }
    }
}
```

让我们来理解 _WHERE_ 子句。`WHERE` 函数接收一个 `ClauseCondiction` 作为参数。示例中的 `age` 和 `name` 用于表示列名，它们是 `Table` 类的扩展属性，它们的类型是
`ClauseElement`，由 KSP 生成。

`ClauseElement` 拥有一系列表示相应的 SQL 操作（`=`、`>`、`<`、`LIKE`、`IN`、`IS` 等等）的操作符。当一个 `ClauseElement` 调用一个操作符时我们将会得到一个 
`ClauseCondiction`。多个 `ClauseCondiction` 可以使用 `AND` 或 `OR` 操作符连接并产生一个新的 `ClauseCondiction`。

SQL 操作符与 SQLlin 操作符的对应关系如下表：

|SQL|SQLlin|
|---|---|
|=|EQ|
|!= |NEQ|
|<|LT|
|<=|LTE|
|>|GT|
|>=|GTE|
|BETWEEN|BETWEEN|
|IN|IN|
|LIKE|LIKE|
|GLOB|GLOB|
|OR|OR|
|AND|AND|
|LIKE ... ESCAPE|LIKE ... ESCAPE|

在 _LIKE_ 的模式中，`%` 匹配任意多个字符，`_` 匹配任意一个字符。要匹配它们本身，可以用 `ESCAPE` 给模式指定一个转义字符（实验性
API）：转义字符之后的 `%`、`_` 和转义字符本身都只匹配它们自己。

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    database {
        PersonTable { table ->
            // 删除名字中带下划线的人：
            // DELETE FROM person WHERE person.name LIKE ? ESCAPE ?，参数为 "%\_%" 和 "\"
            table DELETE WHERE (name LIKE "%\\_%" ESCAPE '\\')
        }
    }
}
```

有时候，我们想要删除表中的所有数据，这时 _DELETE_ 语句可以省略 _WHERE_ 子句：

```SQL
DELETE FROM person
```

在 SQLlin 中我们可以这样写来达到同样的效果：

```kotlin
fun sample() {
    database {
        PersonTable { table ->
            table DELETE X
        }
    }
}
```
`X` 是一个 Kotlin `object`（单例）。

## 更新

_UPDATE_ 语句与 _DELETE_ 语句相似，它同样使用一个 _WHERE_  子句来限制更新条件。但是 _UPDATE_ 语句的不同点在于它拥有一个独特的 _SET_ 子句：

```kotlin
fun sample() {
    database {
        PersonTable { table ->
            table UPDATE SET { age = 5 } WHERE (name NEQ "Tom")
        }
    }
}
```

_SET_ 子句与其他子句不同，它接收一个 lambda 表达式作为参数，你可以在 lambda 中给列设置一个新值。lambda 表达式中的 `age` 是一个由 KSP
生成的可写属性，并且它仅在 _SET_ 子句中可用，它与 _WHERE_ 子句中的只读属性 `age` 不同。

你也可以编写没有 _WHERE_ 子句的 _UPDATE_ 语句用于更新所有的行，但使用它的时候你应该谨慎。

## 事务

在 SQLlin 中使用事务非常简单，你只需要使用 `transaction {...}` 包裹你的 SQL 语句：

```kotlin
fun sample() {
    database {
        transaction {
            PersonTable { table ->
                table INSERT Person(age = 4, name = "Tom")
                table INSERT listOf(
                    Person(age = 10, name = "Nick"),
                    Person(age = 3, name = "Jerry"),
                    Person(age = 8, name = "Jack"),
                )
                table UPDATE SET { age = 5 } WHERE (name NEQ "Tom")
            }
        }
    }
}
```

`transaction {...}` 是 `Database` 的成员函数，将它写在 `TABLE(databaseName) {...}` 函数的内部或外部没有特别限制。

## VACUUM

删除行并不会让数据库文件变小，因为 SQLite 会留着空闲页以便复用。`VACUUM()` 会重建数据库文件，把它压缩到最小的磁盘空间；
`VACUUM_INTO(file)` 则把压缩后的数据库副本写到另一个文件：这是正在使用的数据库在某一时刻的一致备份。两者都是实验性 API：

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class, PlatformDependentSQLiteAPI::class)
fun sample() {
    database {
        VACUUM()
    }
    database {
        VACUUM_INTO("/absolute/path/to/backup.db")
    }
}
```

SQLite 不能在事务中执行 VACUUM，所以在 `transaction {...}` 中调用两者都会抛出 `IllegalStateException`。VACUUM 会重写整个数据库，
所以数据库大时耗时较长，并且需要最多两倍于数据库大小的空闲磁盘空间；它还可能改变没有 INTEGER PRIMARY KEY 的表的 rowid。
`VACUUM_INTO` 需要 SQLite 3.27.0，Android 从 API 30 起才有，所以它标有 [`@PlatformDependentSQLiteAPI`](#依赖-sqlite-版本的-api)。
目标文件必须不存在，在 Android 上路径必须是绝对路径。

## 接下来

你已经学习了如何使用 _INSERT_、_DELETE_ 以及 _UPDATE_ 语句，接下来你将学习 _SELECT_ 语句。 _SELECT_ 语句相比其他语句更复杂，做好准备哦 :)。

- [查询](query-cn.md)
- [并发安全](concurrency-safety-cn.md)
- [SQL 函数](sql-functions-cn.md)
- [高级查询](advanced-query-cn.md)