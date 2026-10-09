# Advanced Query

中文版请见[这里](advanced-query-cn.md)

We have learned basic querying and using SQL functions in querying conditions. Let's learn some advanced skills of querying.

## Compound Queries

A compound query combines the rows of two _SELECT_ statements, as SQL's `UNION`, `UNION ALL`, `INTERSECT` and
`EXCEPT` do. In SQLlin they are infix functions written between the two statements, as in SQL. They are experimental,
so opt in with `@OptIn(ExperimentalDSLDatabaseAPI::class)`:

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

`UNION` combines the rows without duplicates and `UNION_ALL` keeps them, `INTERSECT` keeps the rows both statements
return, and `EXCEPT` the rows of the first that the second doesn't return. Put each _SELECT_ in parentheses, as
_SELECT_ and these operators are infix functions of the same precedence; without them, the code doesn't compile.

Both statements have to read rows of the same type, which is checked at compile time. They can come from different
tables, through a projection, result columns or a join, as described below:

```kotlin
// The names of the people who are both students and teachers
(StudentTable SELECT X<PersonName>()) INTERSECT (TeacherTable SELECT X<PersonName>())
```

Chained operators are evaluated from left to right, as SQLite does, and parentheses group them. SQLite has no
parentheses in compound queries, so SQLlin writes a grouped compound as a subquery:

```kotlin
a UNION b UNION_ALL c    // (a UNION b) UNION ALL c
a UNION (b UNION_ALL c)  // a UNION SELECT * FROM (b UNION ALL c)
```

_ORDER BY_ and _LIMIT_ written after the compound apply to all of it. A statement that ends with its own _ORDER BY_ or
_LIMIT_ keeps them to itself, as SQLlin writes it as a subquery too:

```kotlin
// The 3 oldest and the 3 youngest people
(table SELECT ORDER_BY(age to DESC) LIMIT 3) UNION_ALL (table SELECT ORDER_BY(age to ASC) LIMIT 3)
// The children and the seniors together, by name, 10 at a time
(table SELECT WHERE(age LT 18)) UNION (table SELECT WHERE(age GTE 65)) ORDER_BY (name to ASC) LIMIT 10 OFFSET 20
```

The statements become part of the compound, so they no longer run on their own, and their results can't be read.

### The deprecated `UNION {...}` block

Earlier versions combined _SELECT_ statements with `UNION {...}` and `UNION_ALL {...}` blocks. **They are deprecated
and will be removed in a future version**, because they have bugs that won't be fixed:

- A block's result is typed by the table's row type, but its rows are read into the first _SELECT_'s result type. So
  a union of projections, result columns or joins compiles, and then fails with a `ClassCastException` when its
  results are used.
- A nested block is flattened into the SQL, which SQLite evaluates from left to right, so
  `UNION { a; UNION_ALL { b; c } }` runs as `(a UNION b) UNION ALL c`, not as its nesting suggests.

Use the infix operators instead:

```kotlin
// Deprecated
UNION {
    table SELECT WHERE(age LT 18)
    table SELECT WHERE(age GTE 65)
}
// Instead
(table SELECT WHERE(age LT 18)) UNION (table SELECT WHERE(age GTE 65))
```

## Projection

A `SELECT` reads each row into the table's own row type. To read only some of the columns, declare a narrower
`@Serializable` type whose properties name the columns you want, and give it to the clause function as a type argument:

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

Like a join's result type, a projection type doesn't need `@DBRow`. It works as the type argument of `X<R>()`,
`WHERE<R>(...)`, `ORDER_BY<R>(...)`, `LIMIT<R>(...)` and `GROUP_BY<R>(...)`, after both `SELECT` and
`SELECT_DISTINCT`, and the clauses chained after it keep it. With `SELECT_DISTINCT`, only the projected columns are
compared, so `table SELECT_DISTINCT X<PersonName>()` gives each name once.

The type argument is required. Without it, a `SELECT` reads the table's own row type, even when its result is assigned
to a statement of the projection type.

Each property of a projection type has to be a column of the table, of the same type, and nullable if the column is
nullable, as a `NULL` read into a non-null property would quietly become `0` or an empty string. A projection type that
breaks one of these rules makes the `SELECT` throw an `IllegalArgumentException` when the statement is built, before it
runs. To select expressions such as `count(*)`, use result columns.

## Result Columns

To select an expression, such as an aggregate function, give it a property of the result type with `AS`. Every other
property of the result type is read from its column, as in a projection, so only the expressions are listed: one alone,
or several in a `listOf`:

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

Like a projection type, a result type is a plain `@Serializable` type. A single result column has to be put in
parentheses, as `SELECT` and `AS` are both infix functions. Result columns work after `SELECT` and `SELECT_DISTINCT`, and
can be followed by `WHERE`, `GROUP_BY`, `ORDER_BY` and `LIMIT`. An expression can take the property of a column, too:
`table SELECT (upper(name) AS PersonName::name)` reads every name in upper case.

The property has to have the type of the expression's values, which is checked at compile time. So `count(X)` goes into
a `Long` property, not an `Int` or a `String` one:

| Function | Type of its values |
|---|---|
| `count`, `length`, `instr`, `random` | `Long` |
| `avg`, `round` | `Double` |
| `sum` | `Long` for a column of integers or Booleans, `Double` for a `Float` or `Double` column |
| `max`, `min`, `abs` | the type of their column |
| `upper`, `lower`, `trim`, `ltrim`, `rtrim`, `substr`, `replace`, `printf`, `group_concat` | `String` |

The property also has to be nullable when its expression can be `NULL`, for the same reason as in a projection:

- A function of a nullable column can be `NULL`, except `count`.
- Without `GROUP_BY`, an aggregate query returns one row even when no rows match, in which every aggregate function except
  `count` is `NULL`, and so is every column. So in an aggregate query without `GROUP_BY`, every property but those of
  `count` has to be nullable, as `maxAge: Int?` would be. With `GROUP_BY`, every group has rows, so a property only has to
  be nullable when its column is.

A result type that breaks one of these rules makes the `SELECT` throw an `IllegalArgumentException` when the statement is
built, before it runs, as does a property given two expressions. Only whether `GROUP_BY` follows can't be known yet then,
so a property that needs it is reported when the database scope ends, before any statement of the scope runs.

A property given an expression is found by its name, so it can't be renamed with `@SerialName`. Result columns can select
from a [join](#join) too. Arithmetic, `CASE` and subqueries that give a single value can't be used as expressions yet.

## Join

A join reads the rows of several relations, tables, views or derived tables, together. Start one with
`FROM(relation)`, extend it with join operators, and select from it as from a table. Joins are experimental, so opt in
with `@OptIn(ExperimentalDSLDatabaseAPI::class)`.

The examples use another table, and types for the rows of joins, which don't need `@DBRow`:

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

The join operators are:

| Operator | Followed by | Returns |
| --- | --- | --- |
| `INNER_JOIN` | `ON` or `USING` | the rows of both that match |
| `LEFT_OUTER_JOIN` | `ON` or `USING` | those, and the rows of the relations before it that match none |
| `RIGHT_OUTER_JOIN` | `ON` or `USING` | those, and the rows of the joined relation that match none |
| `FULL_OUTER_JOIN` | `ON` or `USING` | those, and the rows of each that match none |
| `CROSS_JOIN` | nothing | every row of the relations before it with every row of the joined one |
| `NATURAL_JOIN`, `NATURAL_LEFT_OUTER_JOIN`, `NATURAL_RIGHT_OUTER_JOIN`, `NATURAL_FULL_OUTER_JOIN` | nothing | as above, matching the columns of the same names |

`RIGHT_OUTER_JOIN`, `FULL_OUTER_JOIN` and their NATURAL forms need SQLite 3.39.0, which Android has from API 34 on, so
they are marked with `@PlatformDependentSQLiteAPI`, which you have to opt in to, as described in
[APIs That Depend on the SQLite Version](modify-database-and-transaction.md#apis-that-depend-on-the-sqlite-version).

Joins are applied in the order they are written, and each can have its own condition:
`FROM(a) INNER_JOIN b ON (...) LEFT_OUTER_JOIN c ON (...)`. A relation can only be joined once: to join a table with
itself, join a [derived table](#derived-tables) of its rows, which gives it another name.

A join's rows have no type of their own, so a _SELECT_ of a join names the type it reads them into: with
`X<R>()`, with a clause function such as `WHERE<R>(...)`, or with result columns. That type is checked against the
joined relations when the statement is built:

* Every property has to name a column of one of them. Columns of the same name in two relations can't be told apart,
  unless `USING` or a NATURAL join merges them, so select one of them with `AS`, as `PersonTable.name AS StudentScore::name`
  above.
* A property has to be nullable when its column or expression can be NULL. An outer join leaves the columns of a
  relation NULL in the rows where it finds no matching row, and so every expression of them but `count`.

SQLlin writes the columns with their relations' names, so that a condition, a function, _GROUP BY_ or _ORDER BY_ can
name a column that another relation has too. An observed query of a join watches all its relations.

### The Old Join API

Before 2.5.0, joins were written as clauses of _SELECT_: `PersonTable SELECT INNER_JOIN<Student>(TranscriptTable) USING name`,
with `CROSS_JOIN`, `NATURAL_JOIN`, `LEFT_OUTER_JOIN` and the others. They still work, but they don't check the type
their rows are read into, can't join more than two relations each with its own condition, and can't select result
columns. **They will be removed in the next version after 2.5.0**, so write joins as above.

## Subqueries

A _SELECT_ can be part of another query: as a derived table, which the FROM clause or a join reads like a table, or as
the subquery of an `IN` or `EXISTS` condition. It becomes part of that query, so it no longer runs on its own.
Subqueries are experimental, so opt in with `@OptIn(ExperimentalDSLDatabaseAPI::class)`.

The examples use these tables:

```kotlin
@DBRow("person")
@Serializable
data class Person(@PrimaryKey val id: Long?, val name: String, val age: Int)

@DBRow("book")
@Serializable
data class Book(val title: String, val authorId: Long, val price: Double)
```

### Derived Tables

A derived table is named after a view: declare its rows with `@DBView`, as for a [view](modify-database-and-transaction.md#create-view---creating-views),
but no such view has to exist in the database, as the view object only gives the derived table its name and its
columns. `AS` makes a _SELECT_ of the view's rows a derived table:

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

The _SELECT_ has to return the view's rows, which is checked at compile time. A derived table can be selected from,
with any clause, a projection or result columns, and joined, by several queries, but not written to. A query can't read
two derived tables of the same view, as they would have the same name.

A derived table of a table's rows gives the table another name, which a query that joins the table with itself needs.
Give its columns other names too, with result columns, so that the query can tell them from the table's:

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

### IN and EXISTS

`IN` with a _SELECT_ checks whether a value is among those the _SELECT_ returns. The _SELECT_ has to return a single
column of values of the same kind, numbers, text or BLOBs, which is checked when the statement is built, so select a
type with a single property, with a projection or with result columns:

```kotlin
@Serializable
data class AuthorId(val authorId: Long)

// SELECT id,name,age FROM person WHERE person.id IN (SELECT authorId FROM book WHERE book.price>?)
PersonTable SELECT WHERE(PersonTable.id IN (BookTable SELECT WHERE<AuthorId>(BookTable.price GT 30.0)))
```

`EXISTS` checks whether a _SELECT_ returns any row. The _SELECT_ can refer to the columns of the query it is in, which
makes it run for each row of that query, as SQLlin writes columns with their tables' names:

```kotlin
// SELECT id,name,age FROM person WHERE EXISTS (SELECT title,authorId,price FROM book WHERE book.authorId=person.id)
PersonTable SELECT WHERE(EXISTS(BookTable SELECT WHERE(BookTable.authorId EQ PersonTable.id)))
```

Inside the _SELECT_, a column of a table that both queries read is the _SELECT_'s own, as both have the same name, so it
can't refer to the other query's rows of that table.

`NOT` negates a condition, such as `NOT(EXISTS(select))` for the persons without books, or
`NOT(PersonTable.id IN (select))`. It puts the condition in parentheses: `NOT((age LT 18) OR (age GT 65))` is
`NOT (person.age<? OR person.age>?)`.

Subqueries work in the _WHERE_ of _UPDATE_ and _DELETE_ too, and an observed query watches the tables its subqueries
read. Common table expressions (`WITH`) aren't supported yet.

### Scalar Subqueries

A _SELECT_ of a single column can be a value, which is the column's value in its first row, or NULL if it returns no
rows. `select[R::property]` names that column, so that the value has the property's type, and can be compared,
computed with or selected like any [expression](sql-functions.md#expressions):

```kotlin
@Serializable
data class AveragePrice(val value: Double?)

// SELECT title,authorId,price FROM book WHERE book.price>(SELECT avg(book.price) AS value FROM book)
BookTable SELECT WHERE(BookTable.price GT (BookTable SELECT listOf(BookTable.avg(BookTable.price) AS AveragePrice::value))[AveragePrice::value])
```

Its result type has to have that property only, which is checked when the statement is built. As it can be NULL, `AS`
only selects it into a nullable property. Its values are written into the SQL, as the values of any expression are.

## Observed Queries

`Database#observe` turns a _SELECT_ into a `Flow` of its results: it emits them when it is collected, and again whenever
a statement run through the database changes a table the _SELECT_ reads, if the results differ. It is experimental, so
opt in with `@OptIn(ExperimentalDSLDatabaseAPI::class)`:

```kotlin
@OptIn(ExperimentalDSLDatabaseAPI::class)
fun sample(scope: CoroutineScope) {
    val adults: Flow<List<Person>> = database.observe {
        PersonTable SELECT WHERE(PersonTable.age GTE 18)
    }
    scope.launch {
        adults.collect { people ->
            // The adults now, and after every change to the person table that changes them
        }
    }
}
```

The block builds the _SELECT_ to observe, and should only build it, as it runs again each time the results are queried.
Any _SELECT_ works: a join or a compound query watches all of the tables it reads. The queries run on `Dispatchers.IO`,
unless you pass another context: `database.observe(context) { ... }`. Several changes in a row may lead to a single
query.

The changes are counted by SQLite itself, through TEMP triggers SQLlin creates on a table the first time a query that
reads it is observed. So the rows that a foreign key action, such as `ON DELETE CASCADE`, changes in an observed table
count, though no statement names it, while a rolled back transaction doesn't, and neither does an _UPDATE_ or _DELETE_
that matches no rows.

Only the changes made through the same `Database` instance are seen: not those of another instance or connection on
the same file, such as one opened with _sqllin-driver_, nor those of another process.

To combine the results of several _SELECT_ statements, combine the flows that observe them, with `combine` from
kotlinx.coroutines. As each runs its query on its own, a change to the tables of both may briefly show the new results of
one with the old results of the other. When the results come from several tables, a single _SELECT_ with a join gives
them at once.

## Full-Text Search

A full-text search (FTS) table indexes text, so that a query finds the rows containing some words, as a search engine
does. SQLlin supports SQLite's FTS4 and FTS3 tables, which SQLite has on every platform SQLlin supports, Android's
included. They are experimental, so opt in with `@OptIn(ExperimentalDSLDatabaseAPI::class)`.

### Declaring an FTS Table

Annotate a `@DBRow` class with `@Fts4`, and _sqllin-processor_ generates a table object for it, as for any table:

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

`CREATE(ArticleTable)` creates it with:

```sql
CREATE VIRTUAL TABLE articles USING fts4(title,body,note,tokenize=porter,prefix="2",notindexed=note)
```

FTS indexes text, so the columns are the class's `String` and `String?` properties, and as an FTS table's columns have
no types or constraints, they take no constraint annotations. A row's id, its rowid, is read and written through a
`@PrimaryKey` property of type `Long` or `Long?` named `rowid` or `docid`, which isn't a column: `Long?` leaves it for
SQLite to assign, as for a table's `INTEGER PRIMARY KEY`, while `Long` has you supply it. Without one, the rows still
have rowids, which you can't read.

The options of `@Fts4` are:

* `tokenizer`, which splits the text into terms: `FtsTokenizer.SIMPLE`, the default, which folds ASCII to lower case;
  `PORTER`, which also reduces English words to their stems, so that "running" matches "run"; and `UNICODE61`, which
  splits and folds all of Unicode, and removes diacritics
* `tokenizerArgs`, the tokenizer's arguments, such as `["remove_diacritics=2"]` for `UNICODE61`
* `prefix`, the lengths of the prefixes to index, which make prefix queries such as `kot*` faster
* `notIndexed`, the columns whose values are stored, but not searched

`@Fts3` declares an FTS3 table, the older version of FTS4, with only `tokenizer` and `tokenizerArgs`. Prefer FTS4,
unless the table has to be FTS3, such as one an earlier version of your app created.

An FTS table is written to with _INSERT_, _UPDATE_ and _DELETE_, and read with _SELECT_, like any table. SQLite doesn't
let a virtual table be altered or indexed, so `ALTER_ADD_COLUMN` and `CREATE_INDEX` fail on it. To change its columns or
options, rebuild it as described in [Rebuilding a Table](modify-database-and-transaction.md#rebuilding-a-table): for an
FTS table, `withName` returns an FTS table with the same options.

### MATCH

`MATCH` finds the rows that match a query, in any column, or in one:

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

The query is written in FTS's own language. Its terms are split and folded by the table's tokenizer, as the text is:

* `kotlin coroutines`: rows with both terms
* `kotlin OR java`: rows with either term; `OR` has to be in upper case
* `"kotlin multiplatform"`: rows with the phrase
* `corout*`: rows with a term starting with `corout`
* `title:kotlin`: rows with the term in the column `title`
* `kotlin NEAR java`: rows with the terms close to each other

Queries made of these work the same on every platform. Beyond them, FTS has two syntaxes. Android's SQLite has the
standard one, where `-java` leaves out the rows with the term `java`. The SQLite of the JVM driver and of Apple's
platforms has the enhanced one, which adds `AND`, `NOT` and parentheses, and reads `-java` as the term `java` itself. On
Linux and Windows, it depends on how the SQLite your app links was compiled.

### Describing the Matches

In a _SELECT_ with `MATCH`, the functions `snippet`, `offsets` and `matchinfo` of an FTS table object describe how each
row matched. They are selected with [result columns](#result-columns):

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
    // For example: SearchResult(title = "Kotlin Coroutines", excerpt = "Kotlin <b>Coroutines</b>")
}
```

* `snippet(start, end, ellipsis, column, tokens)`: the text around the matched terms, with `start` and `end` around
  them, from `column`, or by default from the column that matches best
* `offsets()`: where the matched terms are, as a text of four numbers per match: the column, the term of the query, and
  the byte offset and size of the match
* `matchinfo(format)`: statistics of the match, as a `ByteArray` of 32-bit unsigned integers in the machine's byte
  order, described by SQLite's documentation of `matchinfo`

FTS4 and FTS3 don't rank the matches: a _SELECT_ returns them in the order of their rowids. To rank them, compute a
score from `matchinfo` in Kotlin. FTS5, which ranks matches, and R*Tree tables aren't supported yet, as Android's
SQLite doesn't have them.

### Observing an FTS Table

An FTS table is a virtual table, which can't have the triggers [observed queries](#observed-queries) count changes with.
So `Database#observe` counts the changes to it from SQLlin's _INSERT_, _UPDATE_ and _DELETE_ on it instead: a change
made with other SQL isn't seen, and neither is a change to an FTS table that a view reads. A rolled back transaction
still counts its statements, which only runs the query once more.

## Finally

You have learned all usages with SQLlin, enjoy it and stay Stay tuned for SQLlin's updates :)