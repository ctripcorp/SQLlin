# 高级查询

我们已经学习了基础查询和在条件查询中使用 SQL 函数。现在我们来学习一些更高级的查询技巧。

## 组合查询

组合查询把两条 _SELECT_ 语句的结果行合并起来，对应 SQL 的 `UNION`、`UNION ALL`、`INTERSECT` 和 `EXCEPT`。在 SQLlin 中，
它们是写在两条语句之间的中缀函数，写法和 SQL 一样。它们是实验性 API，使用时需要 `@OptIn(ExperimentalDSLDatabaseAPI::class)`：

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    lateinit var selectStatement: SelectStatement<Person>
    database {
        PersonTable { table ->
            // SELECT name,age FROM person WHERE age < ? UNION SELECT name,age FROM person WHERE age >= ?
            selectStatement = (table SELECT WHERE(age LT 18)) UNION (table SELECT WHERE(age GTE 65))
        }
    }
}
```

`UNION` 合并结果并去重，`UNION_ALL` 保留重复行，`INTERSECT` 只保留两条语句都返回的行，`EXCEPT` 保留第一条语句返回、而第二条
语句没有返回的行。每条 _SELECT_ 都要加括号，因为 _SELECT_ 和这些运算符都是同优先级的中缀函数；不加括号的话，代码无法编译。

两条语句读出的行必须是同一种类型，这一点在编译期检查。它们可以来自不同的表，借助投影、结果列或 Join，详见下文：

```kotlin
// 既是学生又是老师的人的名字
(StudentTable SELECT X<PersonName>()) INTERSECT (TeacherTable SELECT X<PersonName>())
```

连续的运算符按从左到右的顺序计算，和 SQLite 一致，括号可以改变分组。SQLite 的组合查询里不能写括号，所以 SQLlin 会把分组后的
组合写成子查询：

```kotlin
a UNION b UNION_ALL c    // (a UNION b) UNION ALL c
a UNION (b UNION_ALL c)  // a UNION SELECT * FROM (b UNION ALL c)
```

写在组合之后的 _ORDER BY_ 和 _LIMIT_ 作用于整个组合。某条语句自己末尾的 _ORDER BY_ 或 _LIMIT_ 只作用于它自己，因为 SQLlin 同样会
把它写成子查询：

```kotlin
// 年龄最大的 3 个人和年龄最小的 3 个人
(table SELECT ORDER_BY(age to DESC) LIMIT 3) UNION_ALL (table SELECT ORDER_BY(age to ASC) LIMIT 3)
// 儿童和老人合在一起，按名字排序，每页 10 个
(table SELECT WHERE(age LT 18)) UNION (table SELECT WHERE(age GTE 65)) ORDER_BY (name to ASC) LIMIT 10 OFFSET 20
```

参与组合的语句会成为组合的一部分，因此不再单独执行，也无法读取它们各自的结果。

### 已废弃的 `UNION {...}` 块

以前的版本用 `UNION {...}` 和 `UNION_ALL {...}` 块来合并 _SELECT_ 语句。**它们已经废弃，将在未来的版本中移除**，因为它们存在
不会再修复的 bug：

- 块的结果类型取自表的行类型，结果行却按第一条 _SELECT_ 的结果类型读取。所以对投影、结果列或 Join 做 UNION 时，代码能编译通过，
  但使用结果时会抛出 `ClassCastException`。
- 嵌套的块在生成 SQL 时会被展平，而 SQLite 按从左到右的顺序计算，所以 `UNION { a; UNION_ALL { b; c } }` 实际执行的是
  `(a UNION b) UNION ALL c`，而不是嵌套所表达的含义。

请改用中缀运算符：

```kotlin
// 已废弃
UNION {
    table SELECT WHERE(age LT 18)
    table SELECT WHERE(age GTE 65)
}
// 改为
(table SELECT WHERE(age LT 18)) UNION (table SELECT WHERE(age GTE 65))
```

## 子查询

SQLlin 还不支持子查询，我们将会尽快开发该功能。

## Join

SQLlin 目前支持 join 表。

我们需要另外两个数据库实体：

```kotlin
@DBRow("transcript")
@Serializable
data class Transcript(
    val name: String?,
    val math: Int,
    val english: Int,
)

@Serializable
data class Student(
    val name: String?,
    val age: Int?,
    val math: Int,
    val english: Int,
)

@Serializable
data class CrossJoinStudent(
    val age: Int?,
    val math: Int,
    val english: Int,
)
```

`Transcript` 代表另一张表，`Student` 表示 join 的查询结果的类型（所以 `Student` 不需要被添加 `@DBRow` 注解），它拥有所有 `Person` 和 `Transcript`
所拥有的列名。

### Cross Join

```kotlin
fun joinSample() {
    db {
        PersonTable { table ->
            table SELECT CROSS_JOIN<CrossJoinStudent>(TranscriptTable)
        }
    }
}
```

`CROSS_JOIN` 函数接收一个或多个 `Table` 作为参数。在普通的 _SELECT_ 语句中，该语句的查询结果的类型由 _sqllin-processor_ 生成的
`Table` 决定，但是 _JOIN_ 操作符将会将其改变为指定的类型。在前面的示例中， `CROSS_JOIN` 将该类型改变为了 `CrossJoinStudent`。

注意，由于 SQL 中 _CROSS JOIN_ 自身的特性，如果附带 _CROSS JOIN_ 子句的 _SELECT_ 语句查询的列包含两个表中的同名列，这会导致查询失败。因为
class 中不能包含两个同名的属性。因此请确保 `CROSS_JOIN` 函数转换后的结果类型不包含两个表中的同名列。

### Inner Join

```kotlin
fun joinSample() {
    db {
        PersonTable { table ->
            table SELECT INNER_JOIN<Student>(TranscriptTable) USING name
            table SELECT NATURAL_INNER_JOIN<Student>(TranscriptTable)
            table SELECT INNER_JOIN<CrossJoinStudent>(TranscriptTable) ON (name EQ TranscriptTable.name)
        }
    }
}
```

`INNER_JOIN` 与 `CROSS_JOIN` 非常相似，不同之处在于 `INNER_JOIN` 需要连接一个 `USING` 或 `ON` 子句。如果一个 _INNER JOIN_ 语句没有
`USING` 或 `ON` 子句，那么它是不完整的，但是你的代码仍然可以编译，但它在运行时不会做任何事情。

`NATURAL_INNER_JOIN` 将会产生一个完整的 _SELECT_ 语句（与 `CROSS_JOIN` 相似）。所以，你不能再它末尾连接 `USING` 或 `ON` 子句，这将由
Kotlin 编译器来保证。

注意，带有 `ON` 子句的 `INNER_JOIN` 子句的行为与 `CROSS_JOIN` 相同，你不能 select 在两个表中拥有相同名字的列。

`INNER_JOIN` 拥有一个别名——`JOIN`， `NATURAL_INNER_JOIN` 也拥有一个别名——`NATURAL_JOIN` 。这就像你在 SQL 的 inner join
查询中可以省略 `INNER` 关键字一样。

### Left Outer Join

```kotlin
fun joinSample() {
    db {
        PersonTable { table ->
            table SELECT LEFT_OUTER_JOIN<Student>(TranscriptTable) USING name
            table SELECT NATURAL_LEFT_OUTER_JOIN<Student>(TranscriptTable)
            table SELECT LEFT_OUTER_JOIN<CrossJoinStudent>(TranscriptTable) ON (name EQ TranscriptTable.name)
        }
    }
}
```

`LEFT_OUTER_JOIN` 的用法与 `INNER_JOIN` 非常相似，不同之处仅仅是它们的 API 名字。

## 投影

`SELECT` 默认把每一行读成表自己的行类型。如果只想读取其中一部分列，可以声明一个更窄的 `@Serializable` 类型，用它的属性名
指明要读取的列，再把它作为类型参数交给子句函数：

```kotlin
@Serializable
data class PersonName(
    val name: String,
)

fun sample() {
    lateinit var names: SelectStatement<PersonName>
    lateinit var adultNames: SelectStatement<PersonName>
    database {
        PersonTable { table ->
            // SELECT name FROM person
            names = table SELECT X<PersonName>()
            // SELECT name FROM person WHERE age >= ? ORDER BY name LIMIT 10
            adultNames = table SELECT WHERE<PersonName>(age GTE 18) ORDER_BY name LIMIT 10
        }
    }
}
```

和 Join 的结果类型一样，投影类型不需要 `@DBRow` 注解。它可以作为 `X<R>()`、`WHERE<R>(...)`、`ORDER_BY<R>(...)`、
`LIMIT<R>(...)` 和 `GROUP_BY<R>(...)` 的类型参数，用在 `SELECT` 和 `SELECT_DISTINCT` 之后，后面链式调用的子句也会沿用它。
使用 `SELECT_DISTINCT` 时只比较投影出来的列，所以 `table SELECT_DISTINCT X<PersonName>()` 中每个名字只会出现一次。

类型参数必须显式写出。如果不写，`SELECT` 会读成表自己的行类型，即使它的结果被赋值给一个投影类型的语句也是如此。

投影类型的每个属性都必须是这张表的列，类型与列一致，并且当列可空时属性也必须可空，因为把 `NULL` 读进非空属性时，它会被
悄无声息地读成 `0` 或空字符串。不满足这些规则的投影类型会让 `SELECT` 在构建语句时、执行之前就抛出 `IllegalArgumentException`。
要查询 `count(*)` 这样的表达式，请使用结果列。

## 结果列

要查询一个表达式，比如聚合函数，可以用 `AS` 把它交给结果类型的一个属性。结果类型的其余属性和投影一样，从同名的列读取，
所以只需要列出表达式：单个表达式直接写，多个表达式放进 `listOf`：

```kotlin
@Serializable
data class NameStats(
    val name: String,
    val people: Long,
    val maxAge: Int,
)

@Serializable
data class PersonCount(
    val people: Long,
)

fun sample() {
    lateinit var stats: SelectStatement<NameStats>
    lateinit var adults: SelectStatement<PersonCount>
    database {
        PersonTable { table ->
            // SELECT name,count(*) AS people,max(age) AS maxAge FROM person GROUP BY name
            stats = table SELECT listOf(count(X) AS NameStats::people, max(age) AS NameStats::maxAge) GROUP_BY name
            // SELECT count(*) AS people FROM person WHERE age >= ?
            adults = table SELECT (count(X) AS PersonCount::people) WHERE (age GTE 18)
        }
    }
    val adultCount = adults.getResults().single().people
}
```

和投影类型一样，结果类型就是普通的 `@Serializable` 类型。单个结果列必须加括号，因为 `SELECT` 和 `AS` 都是中缀函数。
结果列可以用在 `SELECT` 和 `SELECT_DISTINCT` 之后，后面可以接 `WHERE`、`GROUP_BY`、`ORDER_BY` 和 `LIMIT`。表达式也可以
占用某一列对应的属性：`table SELECT (upper(name) AS PersonName::name)` 会把所有名字读成大写。

属性的类型必须和表达式的值的类型一致，这一点在编译期检查。所以 `count(X)` 只能交给 `Long` 属性，不能交给 `Int` 或
`String` 属性：

| 函数 | 值的类型 |
|---|---|
| `count`、`length`、`instr`、`random` | `Long` |
| `avg`、`round` | `Double` |
| `sum` | 整数列和 Boolean 列为 `Long`，`Float` 和 `Double` 列为 `Double` |
| `max`、`min`、`abs` | 与它们的列相同 |
| `upper`、`lower`、`trim`、`ltrim`、`rtrim`、`substr`、`replace`、`printf`、`group_concat` | `String` |

当表达式可能为 `NULL` 时，属性还必须可空，原因和投影相同：

- 可空列的函数可能为 `NULL`，`count` 除外。
- 没有 `GROUP_BY` 时，即使没有任何行匹配，聚合查询也会返回一行，这一行里除 `count` 以外的聚合函数都是 `NULL`，所有的列也是
  `NULL`。所以在没有 `GROUP_BY` 的聚合查询中，除了 `count` 对应的属性，其余属性都必须可空，比如要写成 `maxAge: Int?`。
  有 `GROUP_BY` 时，每个分组都至少有一行，所以只有列可空时，属性才必须可空。

不满足这些规则的结果类型，以及同一个属性被交给了两个表达式的情况，都会让 `SELECT` 在构建语句时、执行之前就抛出
`IllegalArgumentException`。唯独后面是否还会接 `GROUP_BY`，在构建时还无法得知，所以需要 `GROUP_BY` 的属性会在数据库作用域
结束时报错，此时作用域中的任何语句都还没有执行。

交给表达式的属性按属性名查找，所以不能用 `@SerialName` 重命名。结果列只能查询单张表：目前还不能和 Join 一起使用，
也不支持算术运算、`CASE` 和子查询。

## 最后

你已经学习了所有的 SQLlin 用法，享受你的 SQLlin 的编程旅程并对它的更新保持关注吧 :)