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

`DISTINCT` 让聚合函数只计算参数中不重复的值，与 SQL 相同：`count(DISTINCT(name))` 就是 `count(DISTINCT person.name)`，即不同名字的
个数。`count`、`sum`、`avg` 和 `group_concat` 都可以接收它，`group_concat` 会用逗号分隔这些不重复的值，因为 SQLite 在这里不允许
其他分隔符。`DISTINCT` 是实验性 API，使用时需要 `@OptIn(ExperimentalDSLDatabaseAPI::class)`。

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
            // SELECT name,count(*) AS people,max(person.age) AS maxAge FROM person GROUP BY person.name
            stats = table SELECT listOf(count(X) AS NameStats::people, max(age) AS NameStats::maxAge) GROUP_BY name
        }
    }
}
```

每个函数的结果都具有 SQLite 为它返回的值的类型，比如 `count` 为 `Long`，`avg` 为 `Double`，`AS` 只能把它交给这个类型的属性。
结果列的详细用法请见[《高级查询》](advanced-query-cn.md#结果列)。

## 表达式

列和函数可以参与运算，在结果列、条件和 `ORDER BY` 中都能用。运算符是实验性 API，使用时需要
`@OptIn(ExperimentalDSLDatabaseAPI::class)`：

```kotlin
@Serializable
data class BookLine(
    val name: String,
    val total: Double,
    val label: String,
)

@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    lateinit var lines: SelectStatement<BookLine>
    database {
        BookTable { table ->
            // SELECT name,(book.price * 1.1) AS total,((book.name || ' by ') || book.author) AS label FROM book
            // WHERE (book.price * 1.1)>? ORDER BY (book.pages % 10) ASC
            lines = table SELECT listOf(
                (price * 1.1) AS BookLine::total,
                (name + " by " + author) AS BookLine::label,
            ) WHERE ((price * 1.1) GT 20.0) ORDER_BY ((pages % 10) to ASC)
        }
    }
}
```

- `+`、`-`、`*`、`/` 和一元 `-` 用于两个同类型的数，或者一个数和一个同类型的值，结果也是这个类型：`Int` 列的 `pages + 1`
  是 `Int`。`%` 只提供给整数类型，因为 SQLite 会先把小数转成整数再求余。字符串的 `+` 就是 SQL 的 `||` 拼接。
- SQLite 的计算结果和 Kotlin 一致，只有两处例外：整数溢出时会变成浮点数，而不是回绕；除以零得 NULL，而不是抛异常。所以
  `/` 和 `%` 的结果可能是 NULL（除数是非零常量时除外），`AS` 只能把它们放进可空的属性。
- 传给运算符或函数的 Kotlin 值会变成字面量。值单独出现时，用 `literal(5)` 把它变成表达式。表达式里的值以字面量的形式写进
  SQL，所以表达式可以用在视图、触发器和索引里，SQLite 不允许这些地方带参数。`ORDER BY` 和 `GROUP BY` 里不能只有一个字面量，
  因为 SQLite 会把 `ORDER BY 2` 理解成第 2 个结果列。
- `CAST(x AS 类型)` 把表达式转换成另一种类型，用于不同类型的数之间的运算，比如 `CAST(pages AS DOUBLE) * price`。类型名和
  SQLlin 建表时给对应 Kotlin 类型的列用的名字一致：`TINYINT`、`SMALLINT`、`INT`、`BIGINT`、`FLOAT`、`DOUBLE`、`TEXT` 和
  `BLOB`。转成整数类型时，小数会截断，字符串会读取开头的数字部分，比如 `CAST('12abc' AS INT)` 是 `12`。

### 更多表达式语法

- `CASE` 返回第一个条件成立的分支的值，没有分支成立时返回 `ELSE` 的值，省略 `ELSE` 时为 NULL：

  ```kotlin
  // CASE WHEN book.pages<50 THEN 'short' WHEN book.pages<200 THEN 'medium' ELSE 'long' END
  CASE(
      WHEN(pages LT 50) THEN literal("short"),
      WHEN(pages LT 200) THEN literal("medium"),
      ELSE = literal("long"),
  )
  ```

  各分支的值类型相同，`CASE` 的类型也就是它，所以 `AS` 会像检查其他表达式一样检查它。
- `IS` 和 `ISNOT` 像 `EQ`、`NEQ` 一样比较两个表达式，区别是 NULL 等于 NULL，所以比较结果是真或假，而不会是 NULL：
  `notes ISNOT literal("vip")` 也会匹配 `notes` 为 NULL 的行，而 `notes NEQ literal("vip")` 会把它们排除掉。它们的作用和
  `IS DISTINCT FROM` 相同，并且所有版本的 SQLite 都支持。
- `COLLATE` 按指定的排序规则（`BINARY`、`NOCASE` 或 `RTRIM`）比较和排序字符串：`(name COLLATE NOCASE) EQ "ann"` 也会匹配
  `'Ann'`，`ORDER_BY((name COLLATE NOCASE) to ASC)` 会忽略大小写排序。`NOCASE` 只忽略 ASCII 字母的大小写。
- `and`、`or`、`inv()`、`shl` 和 `shr` 是 `Int` 和 `Long` 的位运算，名字和 Kotlin 自己的一致。
- 只返回一列的 _SELECT_ 可以作为一个值，详见[标量子查询](advanced-query-cn.md#标量子查询)。

## 更多函数

这些函数同样是实验性 API。它们的参数都可以是表达式，需要值的地方也可以传 Kotlin 值：

> **NULL 处理**：`coalesce`、`ifnull`、`nullif`，比如 `coalesce(nickname, name)`、`ifnull(nickname, "?")`
>
> **多个表达式的标量 max 和 min**：`max(a, b, ...)`、`min(a, b, ...)`
>
> **字符串和 BLOB**：`hex`、`quote`、`unicode`、`randomblob`、`zeroblob`，可以指定要去掉的字符的 `trim`、`ltrim`、`rtrim`，
> 从某个位置开始的 `substr`，多个值的 `printf`，以及参数都可以是表达式的 `substr`、`replace`、`instr`
>
> **数值**：取整的 `round`，以及聚合函数 `total`：结果是 `Double` 的求和，没有匹配的行时是 `0.0` 而不是 NULL
>
> **日期和时间**：`date`、`time`、`datetime`、`julianday`、`strftime`，参数是 `"now"` 这样的时间字符串或表达式，还可以加
> `"+1 day"` 这样的修饰符

只有结果可能为 NULL 时，函数才是可空的：`coalesce` 在所有参数都可能为 NULL 时可空，`nullif` 总是可空；日期时间函数遇到读不懂
的值或修饰符时返回 NULL，所以总是可空，只有不带修饰符的 `datetime("now")` 除外。`typeof` 是 Kotlin 的保留字，所以没有提供。

当前时间、`"localtime"` 和 `"utc"` 修饰符会让日期时间函数的结果随时间变化，`random()` 也是这样，所以索引不能包含这样的函数：
`CREATE_INDEX` 会拒绝它，而 SQLite 要等表里有数据时才会报错。

有些函数需要较新的 SQLite，或者 SQLite 的某个编译选项，而有的平台没有，所以它们都标有
[`@PlatformDependentSQLiteAPI`](modify-database-and-transaction-cn.md#依赖-sqlite-版本的-api)：

| 函数 | 需要 | Android |
|---|---|---|
| `sign` | SQLite 3.35.0 | API 34 |
| `unixepoch` | SQLite 3.38.0 | API 34 |
| `ceil`、`floor`、`trunc`、`sqrt`、`exp`、`ln`、`log10`、`log2`、`pow`、`mod`、`pi` | SQLite 3.35.0，并开启 `SQLITE_ENABLE_MATH_FUNCTIONS` | 所有版本都没有 |

JVM 和 Apple 平台有数学函数；在 Linux 和 Windows 上，取决于应用链接的 SQLite。

最后，让我们来学习[《高级查询》](advanced-query-cn.md)吧。