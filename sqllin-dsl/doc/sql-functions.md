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

Finally, let's learn [Advanced Query](advanced-query.md).