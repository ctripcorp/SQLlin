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

## 可观察查询

`Database#observe` 把一条 _SELECT_ 变成它的结果的 `Flow`：开始收集时发出当前结果；之后每当经由这个数据库执行的语句修改了
这条 _SELECT_ 读取的表，并且结果确实有变化时，再次发出。它是实验性 API，使用时需要 `@OptIn(ExperimentalDSLDatabaseAPI::class)`：

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample(scope: CoroutineScope) {
    val adults: Flow<List<Person>> = database.observe {
        PersonTable SELECT WHERE(PersonTable.age GTE 18)
    }
    scope.launch {
        adults.collect { people ->
            // 当前的成年人，以及之后 person 表每次改变了这个结果之后的成年人
        }
    }
}
```

代码块用来构建要观察的 _SELECT_，并且只应该构建它，因为每次查询结果时它都会重新执行。任何 _SELECT_ 都可以：Join 和组合查询会
观察它们读取的所有表。查询默认在 `Dispatchers.IO` 上执行，也可以传入别的上下文：`database.observe(context) { ... }`。
连续的多次修改可能只引起一次查询。

修改由 SQLite 自己计数：某张表第一次被观察时，SQLlin 会在它上面创建 TEMP 触发器。所以外键动作（比如 `ON DELETE CASCADE`）
修改了被观察的表中的行，即使没有任何语句提到这张表，也会被计入；而回滚的事务不会被计入，没有匹配到任何行的 _UPDATE_ 或
_DELETE_ 也不会。

只有经由同一个 `Database` 实例的修改才能被感知：同一个文件上的其他实例或连接（比如用 _sqllin-driver_ 打开的连接），以及其他
进程所做的修改，都感知不到。

要组合几条 _SELECT_ 的结果，可以用 kotlinx.coroutines 的 `combine` 组合观察它们的几个 Flow。由于每个 Flow 各自执行查询，一次
同时影响两边的表的修改，可能会短暂地呈现一边的新结果和另一边的旧结果。如果结果来自几张表，用一条带 Join 的 _SELECT_ 可以一次
得到。

## 全文搜索

全文搜索（FTS）表会为文本建立索引，让查询像搜索引擎一样找出包含某些词的行。SQLlin 支持 SQLite 的 FTS4 和 FTS3 表，SQLlin
支持的所有平台上的 SQLite 都有它们，Android 也不例外。它们是实验性 API，使用时需要 `@OptIn(ExperimentalDSLDatabaseAPI::class)`。

### 声明 FTS 表

给 `@DBRow` 类加上 `@Fts4`，_sqllin-processor_ 就会像为普通表一样为它生成表对象：

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBRow("articles")
@Fts4(tokenizer = FtsTokenizer.PORTER, prefix = [2], notIndexed = ["note"])
@Serializable
data class Article(
    @PrimaryKey val rowid: Long?,
    val title: String,
    val body: String,
    val note: String?,
)
```

`CREATE(ArticleTable)` 用下面的语句创建它：

```sql
CREATE VIRTUAL TABLE articles USING fts4(title,body,note,tokenize=porter,prefix="2",notindexed=note)
```

FTS 为文本建立索引，所以列就是类中 `String` 和 `String?` 类型的属性；FTS 表的列没有类型和约束，所以它们也不接受约束注解。
行的 id，也就是 rowid，通过一个名为 `rowid` 或 `docid`、类型为 `Long` 或 `Long?` 的 `@PrimaryKey` 属性读写，它不是一列：
`Long?` 表示由 SQLite 分配，和普通表的 `INTEGER PRIMARY KEY` 一样；`Long` 表示由你提供。没有这个属性时，行仍然有 rowid，
只是你读不到。

`@Fts4` 的选项有：

* `tokenizer`，把文本切分成词的分词器：`FtsTokenizer.SIMPLE` 是默认值，把 ASCII 转为小写；`PORTER` 还会把英文单词还原为
  词干，于是 "running" 能匹配 "run"；`UNICODE61` 按 Unicode 切分并转为小写，还会去掉变音符号
* `tokenizerArgs`，分词器的参数，比如给 `UNICODE61` 的 `["remove_diacritics=2"]`
* `prefix`，要建立索引的前缀长度，可以加快 `kot*` 这样的前缀查询
* `notIndexed`，只存储值、不参与搜索的列

`@Fts3` 声明 FTS3 表，它是 FTS4 的旧版本，只有 `tokenizer` 和 `tokenizerArgs` 两个选项。除非表必须是 FTS3（比如你的应用的
早期版本创建的表），否则请使用 FTS4。

FTS 表和普通表一样，用 _INSERT_、_UPDATE_、_DELETE_ 写入，用 _SELECT_ 读取。SQLite 不允许修改虚拟表的结构或给它建索引，
所以 `ALTER_ADD_COLUMN` 和 `CREATE_INDEX` 用在它上面会失败。要修改它的列或选项，请按照[重建表](modify-database-and-transaction-cn.md#重建表)
的方法重建：对于 FTS 表，`withName` 返回的是选项相同的 FTS 表。

### MATCH

`MATCH` 找出在任意一列或某一列中匹配查询的行：

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    lateinit var anyColumn: SelectStatement<Article>
    lateinit var titles: SelectStatement<Article>
    database {
        ArticleTable { table ->
            // SELECT rowid,title,body,note FROM articles WHERE articles MATCH ?
            anyColumn = table SELECT WHERE(table MATCH "kotlin coroutines")
            // SELECT rowid,title,body,note FROM articles WHERE articles.title MATCH ?
            titles = table SELECT WHERE(title MATCH "kotlin")
        }
    }
}
```

查询用的是 FTS 自己的语言，其中的词会和文本一样经过表的分词器切分和转换：

* `kotlin coroutines`：同时包含这两个词的行
* `kotlin OR java`：包含其中任意一个词的行；`OR` 必须大写
* `"kotlin multiplatform"`：包含这个短语的行
* `corout*`：包含以 `corout` 开头的词的行
* `title:kotlin`：`title` 列中包含这个词的行
* `kotlin NEAR java`：这两个词彼此靠近的行

只用这些写法的查询在所有平台上的效果都一样。除此之外，FTS 有两种语法。Android 的 SQLite 使用标准语法，其中 `-java` 表示
排除包含 `java` 的行；JVM 驱动和 Apple 平台的 SQLite 使用增强语法，它增加了 `AND`、`NOT` 和括号，而 `-java` 只被当作 `java`
这个词本身。在 Linux 和 Windows 上，取决于你的应用链接的 SQLite 是如何编译的。

### 描述匹配

在带 `MATCH` 的 _SELECT_ 中，FTS 表对象的函数 `snippet`、`offsets` 和 `matchinfo` 描述每一行是如何匹配的。它们通过
[结果列](#结果列)选取：

```kotlin
@Serializable
data class SearchResult(val title: String, val excerpt: String)

@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    lateinit var results: SelectStatement<SearchResult>
    database {
        ArticleTable { table ->
            // SELECT title,snippet(articles,'<b>','</b>','<b>...</b>',-1,-15) AS excerpt FROM articles WHERE articles MATCH ?
            results = table SELECT listOf(table.snippet() AS SearchResult::excerpt) WHERE (table MATCH "coroutines")
        }
    }
    // 例如：SearchResult(title = "Kotlin Coroutines", excerpt = "Kotlin <b>Coroutines</b>")
}
```

* `snippet(start, end, ellipsis, column, tokens)`：匹配到的词周围的文本，词的前后分别加上 `start` 和 `end`；取自 `column`
  列，默认取自匹配得最好的列
* `offsets()`：匹配到的词所在的位置，是一段文本，每处匹配四个数字：列、查询中的第几个词、匹配的字节偏移量和字节数
* `matchinfo(format)`：匹配的统计数据，是由本机字节序的 32 位无符号整数组成的 `ByteArray`，其含义见 SQLite 的 `matchinfo`
  文档

FTS4 和 FTS3 不会给匹配结果排序：_SELECT_ 按 rowid 的顺序返回它们。要排序的话，请在 Kotlin 中根据 `matchinfo` 计算分数。
能给匹配结果排序的 FTS5 和 R*Tree 表暂不支持，因为 Android 的 SQLite 没有它们。

### 观察 FTS 表

FTS 表是虚拟表，无法拥有[可观察查询](#可观察查询)用来计数修改的触发器。所以 `Database#observe` 改为根据 SQLlin 对它执行的
_INSERT_、_UPDATE_ 和 _DELETE_ 来计数：用其他 SQL 所做的修改感知不到，视图所读取的 FTS 表的修改也感知不到。回滚的事务仍会
计入其中的语句，这只会让查询多执行一次。

## 最后

你已经学习了所有的 SQLlin 用法，享受你的 SQLlin 的编程旅程并对它的更新保持关注吧 :)