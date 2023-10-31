# Concurrency Safety

Before the version `1.2.2`, _sqllin-dsl_ can't ensure the concurrency safety. If
you want to share a `Database` object between different threads, that would lead to
unpredictable consequences. So, the best way is when you want to operate your
database, create a `Database` object, and when you finish your operating, close it immediately.

But, that's very inconvenient, we always have to create a database connection and
close it frequently, that is a waste of resources. For example, if we are developing
an Android app, and in a single page(Activity/Fragment), we hope we can keep a
`Database` object, when we want to operate the database in background threads(or
coroutines), just use it, and, close it in certain lifecycle
functions(`onDestroy`, `onStop`, etc..).

In that time, we should make sure the concurrency safety that we sharing the `Database`
object between different threads(or coroutines). So, start with the version `1.2.2`, we can
use the new API `Database#suspendedScope` to replace the usage of `database {}`. For
example, if we have some old code:

```kotlin
fun sample() {
    database {
        PersonTable { table ->
            table INSERT Person(age = 4, name = "Tom")
            table INSERT listOf(
                Person(age = 10, name = "Nick"),
                Person(age = 3, name = "Jerry"),
                Person(age = 8, name = "Jack"),
            )
        }
    }
}
```
We use the new API `Database#suspendedScope` to replace the `database {}`, it will be like that:

```kotlin
fun sample() {
    database suspendScope {
        PersonTable { table ->
            table INSERT Person(age = 4, name = "Tom")
            table INSERT listOf(
                Person(age = 10, name = "Nick"),
                Person(age = 3, name = "Jerry"),
                Person(age = 8, name = "Jack"),
            )
        }
    }
}
```

The `suspendedScope` is a suspend function. Inside the `suspendedScope`, the all operations are
atomic. That means: If you share the same `Database` object between two coroutines, it can ensure the
`suspendedScope` executing later will wait for the one executing earlier to finish.

## Next Step

- [SQL Functions](sql-functions.md)
- [Advanced Query](advanced-query.md)