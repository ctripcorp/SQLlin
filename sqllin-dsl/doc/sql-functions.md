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
             table SELECT GROUP_BY(name) HAVING (count(X) > 2)
        }
    }
}
```

In [Modify Database and Transaction](modify-database-and-transaction.md), we have introduced _sqllin-processor_ will help us to
generate some `ClauseElement`s to represent column names. SQL functions will receive a `ClauseElement` as a parameter and return
a `ClauseElement` as the result. The functions supported by SQLlin are as follows:

> `count`, `max`, `min`, `avg`, `sum`, `abs`, `upper`, `lower`, `length`

The `count` function has a different point, it could receive `X` as parameter be used for representing `count(*)` in SQL, as shown in the
example above.

SQLlin only supports using functions in conditions now. We will consider supporting using functions after the _SELECT_ keyword in
future versions. Now, if you have similar demands, you can use
[Kotlin Collections API](https://kotlinlang.org/docs/collection-aggregate.html) to handle query results:

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

Finally, let's learn [Advanced Query](advanced-query.md).