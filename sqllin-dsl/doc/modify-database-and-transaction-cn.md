# 修改数据库与事务

在[《开始使用》](getting-start-cn.md)中，我们学习了如何创建 `Database` 实例以及定义你自己的数据库实体。现在我们将开始学习如何在 SQLlin 中编写 SQL 语句。

## 表结构操作

SQLlin 提供了用于管理表结构的类型安全 DSL 操作：CREATE、DROP 和 ALTER（在 API 中称为 ALERT）。

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

### ALTER - 修改表结构

SQLlin 提供了多种 ALTER（ALERT）操作来修改现有的表结构：

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
        PersonTable ALERT_ADD_COLUMN PersonTable.email
    }
}
```

#### 重命名表

将现有表重命名为新名称：

```kotlin
fun sample() {
    database {
        // Rename using Table object
        PersonTable ALERT_RENAME_TABLE_TO NewPersonTable

        // Or rename using old table name as String
        "old_person" ALERT_RENAME_TABLE_TO NewPersonTable
    }
}
```

#### 重命名列

重命名表中的列：

```kotlin
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

从现有表中删除列：

```kotlin
fun sample() {
    database {
        PersonTable DROP_COLUMN PersonTable.email
    }
}
```

**⚠️ 警告**：DROP COLUMN 会永久删除列及其所有数据。请注意，SQLite 的 DROP COLUMN 支持是在 3.35.0 版本中添加的，因此较旧的 SQLite 版本可能需要重建表。

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
                    PersonTable ALERT_ADD_COLUMN PersonTable.email
                    CREATE(AddressTable)
                }
            }
        }
    )
)
```

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

## 接下来

你已经学习了如何使用 _INSERT_、_DELETE_ 以及 _UPDATE_ 语句，接下来你将学习 _SELECT_ 语句。 _SELECT_ 语句相比其他语句更复杂，做好准备哦 :)。

- [查询](query-cn.md)
- [并发安全](concurrency-safety-cn.md)
- [SQL 函数](sql-functions-cn.md)
- [高级查询](advanced-query-cn.md)