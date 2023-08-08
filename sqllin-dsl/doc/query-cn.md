# 查询

_SELECT_ 语句相比其他语句更加复杂，因为 _SELECT_ 语句拥有更多的子句。

## 基础

最简单的用例是查询表内的所有数据：

```kotlin
fun sample() {
    lateinit var selectStatement: SelectStatement<Person>
    database {
        PersonTable { table ->
             selectStatement = table SELECT X
        }
    }
    selectStatement.getResult().forEach { person ->
        println(person)
    }
}
```

`X` 表示没有任何子句，我们已经在 _DELETE_ 语句中见过它了。

_SELECT_ 语句与其他语句的另一个不同点在于它拥有查询结果。所以你需要声明一个类型为 `SelectStatement<T>`
的变量，泛型参数 `T` 是你希望反序列化的数据库实体的类型。你应该将你构建的 _SELECT_ 语句赋值给此变量。

注意，所有的语句只会在 _DatabaseScope_ 结束后执行，我们曾在[《修改数据库与事务》](modify-database-and-transaction-cn.md)中提到过这一点。
所以你必须在 `database { ... }` 外部调用 `getResults` 函数，SQLlin 将会帮助你将查询结果反序列化为你期待的对象。

## 单子句

在 SQL 中，我们常使用一些子句来进行条件查询。这些子句可以被单独使用 _WHERE_、_ORDER BY_、_LIMIT_ 以及
_GROUP BY_ 。示例代码如下所示：

```kotlin
fun sample() {
    database {
        PersonTable { table ->
             table SELECT WHERE(age LTE 5)
             table SELECT ORDER_BY(age to DESC)
             table SELECT LIMIT(3)
             table SELECT GROUP_BY(name)
        }
    }
}
```

在 _ORDER BY_ 子句中，排序方式（`ASC` 或 `DESC`）必须被显式写出。

## 子句连接

有时我们会一次使用多个子句。在 SQL 中，有一些子句必须跟在另一些子句之后，比如 _HAVING_ 跟在 _GROUP BY_ 后面。SQLlin
确保你不会在子句的顺序上出错，子句的连接规则如下表所示：

|Clause/Statement| Can Connect                      |
|---|----------------------------------|
|SELECT| WHERE, ORDER BY, LIMIT, GROUP BY |
|WHERE| LIMIT, ORDER BY, GROUP BY        |
|GROUP BY| HAVING, ORDER BY                 |
|HAVING| ORDER BY, LIMIT                  |
|ORDER BY| LIMIT                            |
|LIMIT| OFFSET                           |
|OFFSET| /                                |

一个带有多子句的 _SELECT_ 如下所示：

```kotlin
fun sample() {
    lateinit var selectStatement: SelectStatement<Person>
    database {
        PersonTable { table ->
             selectStatement = table SELECT WHERE (age LTE 5) GROUP_BY age HAVING (upper(name) EQ "TOM") ORDER_BY (age to DESC) LIMIT 2 OFFSET 1
        }
    }
    selectStatement.getResult().forEach { person ->
        println(person)
    }
}
```

## 接下来

接下来我们将学习如何使用 SQL 函数以及高级查询：

- [SQL 函数](sql-functions-cn.md)
- [高级查询](advanced-query-cn.md)