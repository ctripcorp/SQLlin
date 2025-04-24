# 并发安全

在 `1.2.2` 版本之前， _sqllin-dsl_ 无法保证并发安全。如果你想在不同的线程中共享同一个 `Database`
实例，这可能会导致不可预测的结果。所以最佳的方式是：当你想要操作数据库时，创建一个 `Database`
实例，而当你结束操作时立即关闭它。

但是这非常不方便，我们总是必须频繁地创建数据库连接并关闭，是一种对资源的浪费。举例来说，
如果我们正在开发一款 Android app，并且在单个页面中（Activity/Fragment），我们希望我们可以持有一个
`Database` 实例，当我们想要在后台线程（或协程）中操作数据库时直接使用它，并在某些生命周期函数内将它关闭
（`onDestroy`、`onStop` 等等）。

这种情况下，当我们在不同线程（或协程）中共享 `Database` 实例时，我们应该确保并发安全。所以，从 `1.2.2`
版本开始，我们可以使用新 API `Database#suspendedScope` 来代替旧的 `database {}` 用法。比如说，如果我们有如下旧代码：

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
我们使用新 API `Database#suspendedScope` 来代替旧 `database {}` 后，将会是这样：

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
`suspendedScope` 是一个挂起函数。在 `suspendedScope` 内部所有的操作都是原子性的。这意味着：如果你共享了同一个
`Database` 实例到不同的协程中，它可以保证后执行的 `suspendedScope` 会等待先执行的 `suspendedScope` 执行完成。

## 接下来

- [SQL 函数](sql-functions-cn.md)
- [高级查询](advanced-query-cn.md)