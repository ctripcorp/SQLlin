# SQL 函数

SQLite 拥有一些内置的函数。我们通常会在两个地方使用它们： _SELECT_ 关键字之后以及条件语句中（ _WHERE_ 和 _HAVING_ ）。

在条件语句中使用函数：

```kotlin
fun sample() {
    database {
        PersonTable { table ->
             table SELECT WHERE(abs(age) LTE 5)
             table SELECT GROUP_BY(name) HAVING (count(X) > 2)
        }
    }
}
```
在[《修改数据库与事务》](modify-database-and-transaction-cn.md)中，我们已经介绍过 _sqllin-processor_
会帮助我们生成一些 `ClauseElement` 来表示列名。SQL 函数将会接收一个 `ClauseElement` 作为参数并返回一个
`ClauseElement` 作为结果。SQLlin 支持的函数如下：

> `count`, `max`, `min`, `avg`, `sum`, `abs`, `upper`, `lower`, `length`

`count` 函数有一个不同点，它可以接收一个 `X` 作为参数用于表示 SQL 中的 `count(*)`， 如前面的示例所示。

SQLlin 当前只支持在条件语句中使用函数。我们将会考虑在未来的版本中支持在 _SELECT_ 关键字后使用函数。现在，
如果你有类似的需求，你可以使用 *[Kotlin 集合 API](https://kotlinlang.org/docs/collection-aggregate.html)* 来处理查询结果：

```kotlin
fun sample() {
    lateinit var selectStatement: SelectStatement<Person>
    database {
        PersonTable { table ->
             selectStatement = table SELECT X
        }
    }
    // Get the max value
    selectStatement.getResult().maxOrNull()
    // Get the min value
    selectStatement.getResult().minOrNull()
    // Get the count of query results
    selectStatement.getResult().count()
    // ......
}
```

最后，让我们来学习[《高级查询》](advanced-query-cn.md)吧。