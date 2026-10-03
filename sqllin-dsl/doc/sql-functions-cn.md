# SQL 函数

SQLite 拥有一些内置的函数。我们通常会在两个地方使用它们： _SELECT_ 关键字之后以及条件语句中（ _WHERE_ 和 _HAVING_ ）。

在条件语句中使用函数：

```kotlin
fun sample() {
    database {
        PersonTable { table ->
             table SELECT WHERE(abs(age) LTE 5)
             table SELECT GROUP_BY(name) HAVING (count(X) GT 2)
        }
    }
}
```
在[《修改数据库与事务》](modify-database-and-transaction-cn.md)中，我们已经介绍过 _sqllin-processor_
会帮助我们生成一些 `ClauseElement` 来表示列名。SQL 函数将会接收一个 `ClauseElement` 作为参数并返回一个
`ClauseElement` 作为结果。SQLlin 支持的函数如下：

> **聚合函数**: `count`, `max`, `min`, `avg`, `sum`, `group_concat`
>
> **数值函数**: `abs`, `round`, `random`
>
> **字符串函数**: `upper`, `lower`, `length`, `substr`, `trim`, `ltrim`, `rtrim`, `replace`, `instr`, `printf`

`count` 函数有一个不同点，它可以接收一个 `X` 作为参数用于表示 SQL 中的 `count(*)`， 如前面的示例所示。

要在 _SELECT_ 关键字之后使用函数，可以用 `AS` 把它们交给结果类型的属性：

```kotlin
@Serializable
data class NameStats(
    val name: String,
    val people: Long,
    val maxAge: Int,
)

fun sample() {
    lateinit var stats: SelectStatement<NameStats>
    database {
        PersonTable { table ->
            // SELECT name,count(*) AS people,max(age) AS maxAge FROM person GROUP BY name
            stats = table SELECT listOf(count(X) AS NameStats::people, max(age) AS NameStats::maxAge) GROUP_BY name
        }
    }
}
```

每个函数的结果都具有 SQLite 为它返回的值的类型，比如 `count` 为 `Long`，`avg` 为 `Double`，`AS` 只能把它交给这个类型的属性。
结果列的详细用法请见[《高级查询》](advanced-query-cn.md#结果列)。

最后，让我们来学习[《高级查询》](advanced-query-cn.md)吧。