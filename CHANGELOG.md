# SQLlin Change Log

## v1.1.0

### sqllin-driver

* Enable the `new driver` to replace [SQLiter](https://github.com/touchlab/SQLiter)
* Make some unnecessary APIs be internal(`CursorImpl`, `DatabaseConnectionImpl` and more...)
* Add the new public function `next` in `Cursor`
* Add the new public function: `deleteDatabase`

## v1.0.1/05-13-2023

* Update `Kotlin`'s version to `1.8.20`
* Update `KSP`'s version to `1.8.20-1.0.11`
* Update `kotlinx.serialization`'s version to `1.5.0`

## v1.0.0/12-29-2022

* Add the `ON` clause support
* Update `KSP`'s version to `1.7.20-1.0.8`
* Fix some bugs about `JOIN` clause
* Fix some bugs about unit tests

## v1.0-alpha01/11-29-2022

### Initial Release
* Based on `Kotlin 1.7.20`
* Based on `kotlinx.serialization 1.5.0`