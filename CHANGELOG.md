# SQLlin Change Log

## v1.1.0 / 06-06-2023

### All

* Remove the `iosArm32`, `watchosX86` and `mingwX86` these three targets' support
* Add the new native target support: `watchosDeviceArm64`
* Update `kotlinx.serialization`'s version to `1.5.1`

### sqllin-driver

* Enable the `New Native Driver` to replace [SQLiter](https://github.com/touchlab/SQLiter)
* Make some unnecessary APIs be internal (`CursorImpl`, `DatabaseConnectionImpl` and more...)
* Add the new public function in `Cursor#next`
* Add the new public function `deleteDatabase`
* Add the new public property: `DatabaseConnection#isClosed`
* Deprecated the public property: `DatabaseConnection#closed`

## v1.0.1 / 05-13-2023

### All

* Update `Kotlin`'s version to `1.8.20`

### sqllin-dsl

* Update `kotlinx.serialization`'s version to `1.5.0`

### sqllin-processor

* Update `KSP`'s version to `1.8.20-1.0.11`

## v1.0.0 / 12-29-2022

### All

* Fix some bugs about unit tests


### sqllin-dsl

* Add the `ON` clause support
* Fix some bugs about `JOIN` clause

### sqllin-processor

* Update `KSP`'s version to `1.7.20-1.0.8`

## v1.0-alpha01 / 11-29-2022

### Initial Release

* Based on `Kotlin 1.7.20`
* Based on `KSP 1.7.20-1.0.7`
* Based on `kotlinx.serialization 1.4.1`