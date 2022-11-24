# Advanced Query

We have learned basic query and using SQL functions in query condition. Let's learn some query’s advanced skill.

## Unions

The _UNION_ operator used for merge two _SELECT_ statements' results and these results must be of the same type.

In SQL, _UNION_ operator between with the two _SELECT_ statements, but in SQLlin, we use a higher-order function to
implement _UNION_:

```kotlin
fun sample() {
    lateinit var selectStatement: SelectStatement<Person>
    database {
        PersonTable { table ->
            selectStatement = UNION {
                table SELECT WHERE (age GTE 5)
                table SELECT WHERE (length(name) LTE 8)
            }
        }
    }
}
```

You just need write your _SELECT_ statements in `UNION {...}` block. There must be at least two statements
inside the `UNION {...}` block, if not, you will get a `IllegalStateException` in runtime.

If you want to continuous union multiple _SELECT_ statements, just use `UNION {...}` nesting:

```kotlin
fun sample() {
    lateinit var selectStatement: SelectStatement<Person>
    database {
        PersonTable { table ->
            selectStatement = UNION {
                table SELECT WHERE (age GTE 5)
                UNION_ALL {
                    table SELECT WHERE (length(name) LTE 8)
                    table SELECT WHERE (name EQ "Tom")
                }
            }
        }
    }
}
```

The `UNION_ALL {...}` block equals the `UNION ALL` in SQL. Above code equals the SQL:

```roomsql
SELECT * FROM person WHERE age >= 5
UNION
SELECT * FROM person WHERE length(name) <= 5
UNION ALL
SELECT * FROM person WHERE name = "Tom"
```

## Subqueries

SQLlin doesn't yet support subqueries, we will develop as soon as possible.

## Join

SQLlin supports joining a table now.

We need other two `DBEntity`s:

```kotlin
@DBRow
@Serializable
data class Transcript(
    val name: String?,
    val math: Int,
    val english: Int,
): DBEntity<Transcript> {
    override fun kSerializer(): KSerializer<Transcript> = serializer()
}

@DBRow
@Serializable
data class Student(
    val name: String?,
    val age: Int?,
    val math: Int,
    val english: Int,
): DBEntity<Student> {
    override fun kSerializer(): KSerializer<Student> = serializer()
}
```

The `Transcript` represent a other table. And the `Student` represent the join query results' type, that have all column name that
belong to `Person` and `Transcript`.

### Cross Join

```kotlin
fun joinSample() {
    db {
        TABLE<Person, Unit>(tablePerson) { table ->
            table SELECT CROSS_JOIN<Student>(TABLE<Transcript>(tableTranscript))
        }
    }
}
```

The `CROSS_JOIN` function receive one or multiple `Table`s as parameters. In normal _SELECT_ statements, the statements' results is
same as the `Table`'s first generic parameter, but _JOIN_ operator will change it to specific type. In above sample, _CROSS_JOIN_ change
the type to `Student`.

### Inner Join

```kotlin
fun joinSample() {
    db {
        TABLE<Person, Unit>(tablePerson) { table ->
            table SELECT INNER_JOIN<Student>(TABLE<Transcript>(tableTranscript)) USING name
            table SELECT NATURAL_INNER_JOIN<Student>(TABLE<Transcript>(tableTranscript))
        }
    }
}
```

The `INNER_JOIN` is similar to `CROSS_JOIN`, the deference is `INNER_JOIN` need connect a `USING` clause. If a _INNER JOIN_ statement
without the `USING` clause, it is incomplete, but your code still compiles and will do nothing in runtime. Now, SQLlin just supports `USING`
clause, and doesn't support `ON` clause, it will be supported In future versions.

The `NATURAL_INNER_JOIN` will produce a complete _SELECT_ statement(the same with SQL). So, you can't add `USING` clause to it, this is
guaranteed by the Kotlin compiler.

The `INNER_JOIN` have an alias that named `JOIN`, and `NATURAL_INNER_JOIN` also have an alias that named `NATURAL_JOIN`. That's liked you can
bypass the `INNER` keyword in SQL's inner join query.


### Left Outer Join

```kotlin
fun joinSample() {
    db {
        TABLE<Person, Unit>(tablePerson) { table ->
            table SELECT LEFT_OUTER_JOIN<Student>(TABLE<Transcript>(tableTranscript)) USING name
            table SELECT NATURAL_LEFT_OUTER_JOIN<Student>(TABLE<Transcript>(tableTranscript))
        }
    }
}
```

The `LEFT_OUTER_JOIN`'s usage is very similar to `INNER_JOIN`, but you should very careful to use it. Because of _LEFT OUTER JOIN_ in SQL
own behavior, the queries results will produce some row include null value. If your `DBEntity` that you expect deserialized have some non-null 
properties, you might get a crash while deserializing.

## Finally

You have learned all usage with SQLlin, enjoy it and stay concerned about SQLlin's update :)