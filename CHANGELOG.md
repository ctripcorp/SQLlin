# SQLlin Change Log

- Date format: YYYY-MM-dd

## v1.2.2 / 2023-xx-xx

### All

* Update `Kotlin`'s version to `1.9.20`

### sqllin-dsl

* Add the new native target support: `linuxArm64`
* Add the new API `Database#suspendedScope`, it could be used to ensure concurrency safety([#55](https://github.com/ctripcorp/SQLlin/pull/55))
* Begin with this version, _sqllin-dsl_ depends on _kotlinx.coroutines_ version `1.7.3`
* **Breaking change**: Remove the public class `DBEntity`, we have deprecated it in version `1.1.1`

### sqllin-driver

* Add the new native target support: `linuxArm64`

### sqllin-processor

* Update `KSP`'s version to `1.9.20-1.0.13`

## v1.2.1 / 2023-10-18

### All

* Update `Kotlin`'s version to `1.9.10`

### sqllin-driver

* Fix the problem: [Native driver does not respect isReadOnly](https://github.com/ctripcorp/SQLlin/issues/50). ***On native platforms***. 
Now, if a user set `isReadOnly = true` in `DatabaseConfigurtaion`, the database file must exist. And, if opening in read-write mode 
fails due to OS-level permissions, the user will get a read-only database, and if the user try to modify the database, will receive
a runtime exception. Thanks for [@nbransby](https://github.com/nbransby)

### sqllin-processor

* Update `KSP`'s version to `1.9.10-1.0.13`
* Now, if your data class with `@DBRow` can't be solved or imported successfully(Using `KSNode#validate` to judge), the
`ClauseProcessor` would try to resolve it in second round

## v1.2.0 / 2023-09-19

### sqllin-dsl

* Add the new JVM target

### sqllin-driver

* Add the new JVM target
* **Breaking change**: Remove the public property: `DatabaseConnection#closed`
* The Android (<= 9) target supports to set the `journalMode` and `synchronousMode` now

## v1.1.1 / 2023-08-12

### All

* Update `Kotlin`'s version to `1.9.0`

### sqllin-dsl

* Deprecated the public API `DBEntity`([#36](https://github.com/ctripcorp/SQLlin/pull/36), [#37](https://github.com/ctripcorp/SQLlin/pull/37)), any data classes used in _sqllin-dsl_ don't need to extend `DBEntity` anymore

### sqllin-driver

* Fix a bug about empty `ByteArray` on native platforms([#30](https://github.com/ctripcorp/SQLlin/pull/30))

### sqllin-processor

* Update `KSP`'s version to `1.9.0-1.0.13`

## v1.1.0 / 2023-06-06

### All

* Remove the `iosArm32`, `watchosX86` and `mingwX86` these three targets' support
* Add the new native target support: `watchosDeviceArm64`

### sqllin-dsl

* Update `kotlinx.serialization`'s version to `1.5.1`

### sqllin-driver

* Enable the `New Native Driver` to replace [SQLiter](https://github.com/touchlab/SQLiter)
* Make some unnecessary APIs be internal (`CursorImpl`, `DatabaseConnectionImpl` and more...)
* Add the new public function in `Cursor#next`
* Add the new public function `deleteDatabase`
* Add the new public property: `DatabaseConnection#isClosed`
* Deprecated the public property: `DatabaseConnection#closed`

## v1.0.1 / 2023-05-14

### All

* Update `Kotlin`'s version to `1.8.20`

### sqllin-dsl

* Update `kotlinx.serialization`'s version to `1.5.0`

### sqllin-processor

* Update `KSP`'s version to `1.8.20-1.0.11`

## v1.0.0 / 2022-12-29

### All

* Fix some bugs about unit tests


### sqllin-dsl

* Add the `ON` clause support
* Fix some bugs about `JOIN` clause

### sqllin-processor

* Update `KSP`'s version to `1.7.20-1.0.8`

## v1.0-alpha01 / 2022-11-29

### Initial Release

* Based on `Kotlin 1.7.20`
* Based on `KSP 1.7.20-1.0.7`
* Based on `kotlinx.serialization 1.4.1`