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
            // SELECT name FROM person WHERE age >= ? ORDER BY person.name LIMIT 10
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
            // SELECT name,count(*) AS people,max(person.age) AS maxAge FROM person GROUP BY person.name
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

交给表达式的属性按属性名查找，所以不能用 `@SerialName` 重命名。结果列也可以从 [Join](#join) 中查询。算术运算、`CASE` 和只返回
单个值的子查询暂时还不能作为表达式使用。

## Join

Join 把多个关系（表、视图或派生表）的行合在一起读取。用 `FROM(relation)` 开始一个 Join，用 Join 运算符扩展它，然后像查询表一样
从中查询。Join 是实验性 API，使用时需要 `@OptIn(ExperimentalDSLDatabaseAPI::class)`。

示例用到另一张表，以及几个表示 Join 结果的类型，它们不需要 `@DBRow`：

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
data class StudentScore(
    val name: String?,
    val tests: Long,
    val bestMath: Int?,
)
```

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun joinSample() {
    lateinit var students: SelectStatement<Student>
    lateinit var scores: SelectStatement<StudentScore>
    database {
        // SELECT name,person.age,transcript.math,transcript.english FROM person INNER JOIN transcript USING (name)
        students = (FROM(PersonTable) INNER_JOIN TranscriptTable USING PersonTable.name) SELECT X<Student>()
        // SELECT person.name AS name,count(transcript.math) AS tests,max(transcript.math) AS bestMath
        //     FROM person LEFT OUTER JOIN transcript ON person.name = transcript.name GROUP BY person.name
        scores = (FROM(PersonTable) LEFT_OUTER_JOIN TranscriptTable ON (PersonTable.name EQ TranscriptTable.name)) SELECT listOf(
            PersonTable.name AS StudentScore::name,
            PersonTable.count(TranscriptTable.math) AS StudentScore::tests,
            PersonTable.max(TranscriptTable.math) AS StudentScore::bestMath,
        ) GROUP_BY PersonTable.name
    }
}
```

Join 运算符有：

| 运算符 | 后接 | 返回 |
| --- | --- | --- |
| `INNER_JOIN` | `ON` 或 `USING` | 两边匹配的行 |
| `LEFT_OUTER_JOIN` | `ON` 或 `USING` | 上述行，加上它之前的关系中没有匹配的行 |
| `RIGHT_OUTER_JOIN` | `ON` 或 `USING` | 上述行，加上被连接的关系中没有匹配的行 |
| `FULL_OUTER_JOIN` | `ON` 或 `USING` | 上述行，加上两边各自没有匹配的行 |
| `CROSS_JOIN` | 无 | 它之前的关系的每一行与被连接关系的每一行的组合 |
| `NATURAL_JOIN`、`NATURAL_LEFT_OUTER_JOIN`、`NATURAL_RIGHT_OUTER_JOIN`、`NATURAL_FULL_OUTER_JOIN` | 无 | 同上，按同名的列匹配 |

`RIGHT_OUTER_JOIN`、`FULL_OUTER_JOIN` 和它们的 NATURAL 形式需要 SQLite 3.39.0，Android 从 API 34 起才具备，所以它们标有
`@PlatformDependentSQLiteAPI`，需要按[依赖 SQLite 版本的 API](modify-database-and-transaction-cn.md#依赖-sqlite-版本的-api) 所说的
方式声明同意。

Join 按书写的顺序执行，每一个都可以有自己的条件：`FROM(a) INNER_JOIN b ON (...) LEFT_OUTER_JOIN c ON (...)`。一个关系只能被连接
一次：要让表和自身 Join，请连接由它的行构成的[派生表](#派生表)，这相当于给它起了另一个名字。

Join 的行没有自己的类型，所以从 Join 查询时要指定读取成的类型：用 `X<R>()`、`WHERE<R>(...)` 这类子句函数，或者结果列。构建语句时，
会对照参与 Join 的关系检查这个类型：

* 每个属性都必须是某个关系的列。两个关系中同名的列无法区分，除非 `USING` 或 NATURAL Join 合并了它们，所以请用 `AS` 选取其中一个，
  就像上面的 `PersonTable.name AS StudentScore::name`。
* 列或表达式可能为 NULL 时，属性必须可空。外连接在找不到匹配行的地方，会让一个关系的列为 NULL，因此除 `count` 以外，关于这些列的
  表达式也是如此。

SQLlin 写列时都带上关系的名字，所以条件、函数、_GROUP BY_ 和 _ORDER BY_ 都能使用其他关系中也有的列名。可观察查询会观察 Join 中的
所有关系。

### 旧的 Join API

在 2.5.0 之前，Join 写作 _SELECT_ 的子句：`PersonTable SELECT INNER_JOIN<Student>(TranscriptTable) USING name`，以及 `CROSS_JOIN`、
`NATURAL_JOIN`、`LEFT_OUTER_JOIN` 等。它们仍然可用，但不会检查行被读取成的类型，无法连接两个以上各带条件的关系，也不能选取结果列。
**它们将在 2.5.0 之后的下一个版本中删除**，请按上面的方式书写 Join。

## 子查询

一条 _SELECT_ 可以成为另一条查询的一部分：作为派生表，在 FROM 或 Join 中像表一样被读取；或者作为 `IN`、`EXISTS` 条件里的子查询。
它会成为那条查询的一部分，所以不会再单独执行。子查询是实验性 API，使用时需要 `@OptIn(ExperimentalDSLDatabaseAPI::class)`。

示例使用这两张表：

```kotlin
@DBRow("person")
@Serializable
data class Person(@PrimaryKey val id: Long?, val name: String, val age: Int)

@DBRow("book")
@Serializable
data class Book(val title: String, val authorId: Long, val price: Double)
```

### 派生表

派生表以一个视图命名：像[视图](modify-database-and-transaction-cn.md#create-view---创建视图)一样用 `@DBView` 声明它的行，但数据库里
不需要真的有这个视图，视图对象只是为派生表提供名字和列。`AS` 把一条返回该视图行类型的 _SELECT_ 变成派生表：

```kotlin
@DBView("author_books")
@Serializable
data class AuthorBooks(val authorId: Long, val books: Long)

@Serializable
data class PersonBooks(val name: String, val books: Long)

@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample() {
    lateinit var prolific: SelectStatement<AuthorBooks>
    lateinit var personBooks: SelectStatement<PersonBooks>
    database {
        val authorBooks = BookTable { table ->
            table SELECT listOf(count(X) AS AuthorBooks::books) GROUP_BY authorId
        } AS AuthorBooksView
        // SELECT authorId,books FROM (SELECT authorId,count(*) AS books FROM book GROUP BY book.authorId) AS author_books
        //     WHERE author_books.books>?
        prolific = authorBooks SELECT WHERE(AuthorBooksView.books GT 1)
        // SELECT person.name,author_books.books FROM person INNER JOIN (SELECT ...) AS author_books ON person.id=author_books.authorId
        personBooks = (FROM(PersonTable) INNER_JOIN authorBooks ON (PersonTable.id EQ AuthorBooksView.authorId)) SELECT X<PersonBooks>()
    }
}
```

_SELECT_ 必须返回视图的行类型，这在编译期检查。派生表可以被多条查询读取：可以用任意子句、投影或结果列从中查询，也可以参与 Join，
但不能写入。一条查询不能读取同一个视图的两个派生表，因为它们的名字相同。

由一张表的行构成的派生表相当于给这张表起了另一个名字，表和自身 Join 时就需要这样做。同时用结果列给它的列也起别的名字，让查询能
把它们和表自己的列区分开：

```kotlin
@DBView("elder")
@Serializable
data class Elder(val elderName: String, val elderAge: Int)

@Serializable
data class YoungerAndElder(val name: String, val elderName: String)

database {
    val elders = PersonTable { table ->
        table SELECT listOf(name AS Elder::elderName, age AS Elder::elderAge)
    } AS ElderView
    // SELECT person.name,elder.elderName FROM person
    //     INNER JOIN (SELECT person.name AS elderName,person.age AS elderAge FROM person) AS elder ON elder.elderAge>person.age
    pairs = (FROM(PersonTable) INNER_JOIN elders ON (ElderView.elderAge GT PersonTable.age)) SELECT X<YoungerAndElder>()
}
```

### IN 和 EXISTS

带 _SELECT_ 的 `IN` 检查一个值是否在 _SELECT_ 返回的值之中。_SELECT_ 必须只返回一列、并且是同一类的值（数字、文本或 BLOB），
这在构建语句时检查，所以请用投影或结果列选取一个只有一个属性的类型：

```kotlin
@Serializable
data class AuthorId(val authorId: Long)

// SELECT id,name,age FROM person WHERE person.id IN (SELECT authorId FROM book WHERE book.price>?)
PersonTable SELECT WHERE(PersonTable.id IN (BookTable SELECT WHERE<AuthorId>(BookTable.price GT 30.0)))
```

`EXISTS` 检查一条 _SELECT_ 是否返回了任意一行。由于 SQLlin 写列时都带上表名，_SELECT_ 可以引用外层查询的列，这会让它对外层查询的
每一行执行一次：

```kotlin
// SELECT id,name,age FROM person WHERE EXISTS (SELECT title,authorId,price FROM book WHERE book.authorId=person.id)
PersonTable SELECT WHERE(EXISTS(BookTable SELECT WHERE(BookTable.authorId EQ PersonTable.id)))
```

在 _SELECT_ 内部，两条查询都读取的表的列指的是 _SELECT_ 自己的，因为两者名字相同，所以它不能引用外层查询中这张表的行。

`NOT` 对条件取反，比如用 `NOT(EXISTS(select))` 找出没有书的人，或者 `NOT(PersonTable.id IN (select))`。它会给条件加上括号：
`NOT((age LT 18) OR (age GT 65))` 生成的是 `NOT (person.age<? OR person.age>?)`。

子查询同样可以用在 _UPDATE_ 和 _DELETE_ 的 _WHERE_ 中，可观察查询也会观察子查询读取的表。公用表表达式（`WITH`）暂不支持。

### 标量子查询

只返回一列的 _SELECT_ 可以作为一个值，也就是这一列在第一行的值；没有返回任何行时为 NULL。用 `select[R::property]` 指明这一列，
这个值就有了属性的类型，可以像任何[表达式](sql-functions-cn.md#表达式)一样参与比较、运算或被选取：

```kotlin
@Serializable
data class AveragePrice(val value: Double?)

// SELECT title,authorId,price FROM book WHERE book.price>(SELECT avg(book.price) AS value FROM book)
BookTable SELECT WHERE(BookTable.price GT (BookTable SELECT listOf(BookTable.avg(BookTable.price) AS AveragePrice::value))[AveragePrice::value])
```

它的结果类型只能有这一个属性，这在构建语句时检查。它可能为 NULL，所以 `AS` 只能把它放进可空的属性。和其他表达式一样，它里面的
值会写进 SQL。

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