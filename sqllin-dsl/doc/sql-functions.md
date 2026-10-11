# SQL Functions

中文版请见[这里](sql-functions-cn.md)

SQLite has many built-in functions. We usually would use them in two places: after _SELECT_ keyword
or in conditions (use for _WHERE_ and _HAVING_).

Using functions in conditions like this:

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

In [Modify Database and Transaction](modify-database-and-transaction.md), we have introduced _sqllin-processor_ will help us to
generate some `ClauseElement`s to represent column names. SQL functions will receive a `ClauseElement` as a parameter and return
a `ClauseElement` as the result. The functions supported by SQLlin are as follows:

> **Aggregate functions**: `count`, `max`, `min`, `avg`, `sum`, `group_concat`
>
> **Numeric functions**: `abs`, `round`, `random`
>
> **String functions**: `upper`, `lower`, `length`, `substr`, `trim`, `ltrim`, `rtrim`, `replace`, `instr`, `printf`

The `count` function has a different point, it could receive `X` as parameter be used for representing `count(*)` in SQL, as shown in the
example above.

`DISTINCT` makes an aggregate function take only the distinct values of its argument, as in SQL: `count(DISTINCT(name))`
is `count(DISTINCT person.name)`, the number of different names. `count`, `sum`, `avg` and `group_concat` take it, and
`group_concat` separates the distinct values with commas, as SQLite allows no other separator there. `DISTINCT` is
experimental, so opt in with `@OptIn(ExperimentalDSLDatabaseAPI::class)`.

To use functions after the _SELECT_ keyword, select them into properties of a result type with `AS`:

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

Each function's result has the type of the values SQLite returns for it, such as `Long` for `count` and `Double` for
`avg`, and `AS` only selects it into a property of that type. [Advanced Query](advanced-query.md#result-columns) describes
result columns in detail.

## Expressions

Columns and functions can be computed with, in result columns, conditions and `ORDER BY` alike. The operators are
experimental, so opt in with `@OptIn(ExperimentalDSLDatabaseAPI::class)`:

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

- `+`, `-`, `*`, `/` and the unary `-` compute with two numbers of the same type, or a number and a value of its type,
  and give a number of that type: `pages + 1` of an `Int` column is an `Int`. `%` is only given to integer types, as
  SQLite converts real numbers to integers for it. `+` concatenates strings, SQL's `||`.
- SQLite computes as Kotlin does, except in two cases: an integer that overflows becomes a real number rather than
  wrapping around, and a division by zero is NULL rather than an exception. So `/` and `%` can be NULL, unless the
  divisor is a value other than zero, and `AS` only selects them into a nullable property.
- A Kotlin value given to an operator or a function becomes a literal. Where a value stands alone, `literal(5)` makes it
  an expression. Values in expressions are written into the SQL, as literals, so an expression can be part of a view, a
  trigger or an index, which SQLite doesn't let take parameters. A literal on its own can't be in `ORDER BY` or
  `GROUP BY`, as SQLite reads `ORDER BY 2` as the second result column.
- `CAST(x AS type)` converts an expression to another type, to compute with numbers of different types, as in
  `CAST(pages AS DOUBLE) * price`. The types are named as SQLlin names the columns of that Kotlin type: `TINYINT`,
  `SMALLINT`, `INT`, `BIGINT`, `FLOAT`, `DOUBLE`, `TEXT` and `BLOB`. An integer type truncates a real number, and reads a
  string as far as it is a number, as `CAST('12abc' AS INT)` is `12`.

### More Expression Syntax

- `CASE` gives the value of the first branch whose condition holds, or `ELSE` where none does, and NULL where `ELSE` is
  omitted:

  ```kotlin
  // CASE WHEN book.pages<50 THEN 'short' WHEN book.pages<200 THEN 'medium' ELSE 'long' END
  CASE(
      WHEN(pages LT 50) THEN literal("short"),
      WHEN(pages LT 200) THEN literal("medium"),
      ELSE = literal("long"),
  )
  ```

  The branches have values of the same type, which `CASE` has, so `AS` checks it as it checks any expression.
- `IS` and `ISNOT` compare two expressions as `EQ` and `NEQ` do, except that NULL equals NULL, so the comparison is true
  or false rather than NULL: `notes ISNOT literal("vip")` matches the rows whose `notes` are NULL too, which
  `notes NEQ literal("vip")` leaves out. They do what `IS DISTINCT FROM` does, on every SQLite version.
- `COLLATE` compares and sorts a string by a collation, `BINARY`, `NOCASE` or `RTRIM`:
  `(name COLLATE NOCASE) EQ "ann"` matches `'Ann'` too, and `ORDER_BY((name COLLATE NOCASE) to ASC)` sorts ignoring
  case. `NOCASE` only ignores the case of the ASCII letters.
- `and`, `or`, `inv()`, `shl` and `shr` are the bitwise operators of `Int` and `Long`, named as Kotlin names them.
- A _SELECT_ of a single column can be a value, as described in [Scalar Subqueries](advanced-query.md#scalar-subqueries).

## More Functions

These functions are experimental too. Their arguments are expressions, and a Kotlin value where one is given:

> **NULL**: `coalesce`, `ifnull`, `nullif`, as in `coalesce(nickname, name)` or `ifnull(nickname, "?")`
>
> **Scalar max and min** of several expressions: `max(a, b, ...)`, `min(a, b, ...)`
>
> **Strings and BLOBs**: `hex`, `quote`, `unicode`, `randomblob`, `zeroblob`, `trim`, `ltrim` and `rtrim` with the
> characters to remove, `substr` from a position, `printf` of several values, and `substr`, `replace` and `instr` with
> expressions as all their arguments
>
> **Numbers**: `round` to an integer, and the aggregate `total`, a `Double` sum that is `0.0` rather than NULL when no
> rows match
>
> **Date and time**: `date`, `time`, `datetime`, `julianday` and `strftime`, of a time string such as `"now"` or of an
> expression, with modifiers such as `"+1 day"`

A function is NULL only where its result can be: `coalesce` where all its arguments can be, `nullif` always, and a date
and time function for a value or modifier it can't read, so always, except for `datetime("now")` without modifiers.
`typeof` isn't available, as it is a reserved word in Kotlin.

The current time, and the `"localtime"` and `"utc"` modifiers, make a date and time function give different results over
time, as `random()` does, so an index can't hold such a function: `CREATE_INDEX` rejects it, where SQLite would only
fail once the table has rows.

Some functions need a newer SQLite, or a compile-time option of SQLite, than some platforms have, so they are marked
with [`@PlatformDependentSQLiteAPI`](modify-database-and-transaction.md#apis-that-depend-on-the-sqlite-version):

| Function | Needs | Android |
|---|---|---|
| `sign` | SQLite 3.35.0 | API 34 |
| `unixepoch` | SQLite 3.38.0 | API 34 |
| `ceil`, `floor`, `trunc`, `sqrt`, `exp`, `ln`, `log10`, `log2`, `pow`, `mod`, `pi` | SQLite 3.35.0 with `SQLITE_ENABLE_MATH_FUNCTIONS` | None |

The math functions are on the JVM and Apple's platforms, while on Linux and Windows they depend on the SQLite the app
links.

Finally, let's learn [Advanced Query](advanced-query.md).