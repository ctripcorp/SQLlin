# Query

中文版请见[这里](query-cn.md)

The _SELECT_ statement is more complex than others, because _SELECT_ statement have more clauses.

## Basic

The simplest usage is query all data in the table:

```kotlin
fun sample() {
    lateinit var selectStatement: SelectStatement<Person>
    database {
        PersonTable { table ->
             selectStatement = table SELECT X
        }
    }
    selectStatement.getResult().forEach { person ->
        println(person)
    }
}
```
The `X` represent without any clause, we’ve seen it in _DELETE_ statements.

The _SELECT_ statement has query results, this is another difference from other statements. So, you need declare a variable that
type is `SelectStatement<T>`. The generic parameter `T` is your `DBEntity` that you expect to deserialize. You should assign _SELECT_ statement you built to this variable.

Note, all statements will only be executed when the _DatabaseScope_ ends, we mentioned this in the [Modify database and transaction](modify-database-and-transaction.md).
So, you must invoke the `getResults` function outside the `database { ... }` block, SQLlin will help you deserialize query results to objects that you expected.

## Single Clause

In SQL, we usually use some clauses to make a conditional query. These clauses could be used alone: _WHERE_, _ORDER BY_, _LIMIT_ and 
_GROUP BY_. The sample code like this:

```kotlin
fun sample() {
    database {
        PersonTable { table ->
             table SELECT WHERE(age LTE 5)
             table SELECT ORDER_BY(age to DESC)
             table SELECT LIMIT(3)
             table SELECT GROUP_BY(name)
        }
    }
}
```

In _ORDER BY_ clause, The sorting method (`ASC` or `DESC`) must be explicitly written.

## Clause Connection

Sometimes we need use multiple clauses once. In SQL, some clauses must be used after other clauses. For example, the _HAVING_ behind with
the _GROUP BY_. SQLlin makes sure you don't make mistakes in the order of clauses, the clauses connection regular like this chart: 

|Clause/Statement| Can Connect                      |
|---|----------------------------------|
|SELECT| WHERE, ORDER BY, LIMIT, GROUP BY |
|WHERE| LIMIT, ORDER BY, GROUP BY        |
|GROUP BY| HAVING, ORDER BY                 |
|HAVING| ORDER BY, LIMIT                  |
|ORDER BY| LIMIT                            |
|LIMIT| OFFSET                           |
|OFFSET| /                                |

A multiple clauses _SELECT_ statement like this:

```kotlin
fun sample() {
    lateinit var selectStatement: SelectStatement<Person>
    database {
        PersonTable { table ->
             selectStatement = table SELECT WHERE (age LTE 5) GROUP_BY age HAVING (upper(name) EQ "TOM") ORDER_BY (age to DESC) LIMIT 2 OFFSET 1
        }
    }
    selectStatement.getResult().forEach { person ->
        println(person)
    }
}
```

## Next Step

Next, we will learn how to use SQL functions and advanced query：

- [SQL Functions](sql-functions.md)
- [Advanced Query](advanced-query.md)