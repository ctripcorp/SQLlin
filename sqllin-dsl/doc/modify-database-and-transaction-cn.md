# 修改数据库与事务

在[《开始使用》](getting-start-cn.md)中，我们学习了如何创建 `Database` 实例以及定义你自己的 `DBEntity`。现在我们将开始学习如何在 SQLlin 中编写 SQL 语句。

## 插入

`Database` 类重载了类型为 `<T> Database.(Database.() -> T) -> T` 的函数操作符。当你调用该操作符函数时，它将产生一个 _DatabaseScope_ （数据库作用域）。
没错，它是该操作符函数的 lambda 表达式参数。任何 SQL 语句都必须写在 _DatabaseScope_ 内。并且当 _DatabaseScope_ 结束的时候内部的 SQL 语句才会执行。

你已经知道， _INSERT_、_DELETE_、_UPDATE_ 以及 _SELECT_ SQL 语句用于操作表。所以在你编写你的 SQL 语句之前，你还需要获取一个 `Table` 实例，就像这样：

```kotlin
private val database = Database(name = "Person.db", path = getGlobalPath(), version = 1)

fun sample() {
    database {
        PersonTable { table ->
            // 编写你的 SQL 语句...
        }
    }
}
```
`PersonTable` 由 _sqllin_processor_ 生成，这是因为 `Person` 类被添加了 `@DBRow` 注解。任何被添加了 `@DBRow`
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

_DELETE_ 语句将会比 _INSERT_ 语句稍微复杂。SQLlin 不像 [Room](https://developer.android.com/training/data-storage/room)
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

让我们来理解 _WHERE_ 子句。`WHERE` 函数接收一个 `ClauseCondiction` 座位参数。示例中的 `age` 和 `name` 用于表示列名，它们是 `Table` 类的扩展属性，它们的类型是
`ClauseElement`，由 KSP 生成。

`ClauseElement` 拥有一系列表示 SQL 操作的操作符，比如：`=`、`>`、`<`、`LIKE`、`IN`、`IS` 等等。当一个 `ClauseElement` 调用一个操作符时我们将会得到一个 
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

你也可以编写没有 _WHERE_ 子句的 _UPDATE_ 语句用于删除所有的行，但使用它的时候你应该谨慎。

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

你已经学习了如何使用 _INSERT_、_DELETE_ 以及 _UPDATE_ 语句，接下来你将学习 _SELECT_ 语句。 _SELECT_ 语句相比其他语句更复杂，做好准备噢 :)。

- [查询](query-cn.md)
- [SQL 函数](sql-functions-cn.md)
- [高级查询]()