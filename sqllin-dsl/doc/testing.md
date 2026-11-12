# Testing

中文版请见[这里](testing-cn.md)

Test the code that uses SQLlin against a real SQLite database, as SQLlin's own tests do, rather than against a fake kept
in memory. A fake behaves as it was written, not as SQLite does: it returns rows in the order they were inserted, for
example, where SQLite returns the rows of a query without _ORDER BY_ in the order of the index it reads. This guide
describes where a real database runs on each platform, what such a test can and can't tell, and how to test a migration.

## Where a Real Database Runs

| Tests | The SQLite they run | What they need |
|-------|---------------------|----------------|
| JVM | The one bundled in [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) | Nothing |
| Android, on the host | Robolectric's, under Android's real framework code | The setup below |
| macOS and Apple's simulators | The system's | Nothing |
| Linux and Windows | The one the test binary links | A `libsqlite3` to link, see the note about `linkerOpts` in [Getting Start](getting-start.md) |

A JVM test takes any directory as the path of its database:

```kotlin
private val path = Files.createTempDirectory("database").toString().toDatabasePath()
```

Write the tests once, in a class of `commonTest` that takes the `DatabasePath`, and call them from a test class of each
platform, which only knows how to get the path. SQLlin's own tests are written so.

### Android Host Tests With Robolectric

[Robolectric](https://robolectric.org) runs Android's framework on the JVM, so the Android driver of SQLlin runs as it
does on a device, without an emulator. With the Android Kotlin Multiplatform library plugin, the tests live in the
`androidHostTest` source set:

```kotlin
kotlin {
    android {
        withHostTest {
            isIncludeAndroidResources = true
        }
    }
    sourceSets {
        getByName("androidHostTest").dependencies {
            implementation("junit:junit:$junitVersion")
            implementation("androidx.test:core:$androidxTestVersion")
            implementation("org.robolectric:robolectric:$robolectricVersion")
        }
    }
}

// Robolectric reflects into JDK internals for the newer SDK levels
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
```

With Robolectric 4.17 on JDK 21, the tests of SDK 36 and 37 fail without that JVM argument, with `Failed to interact
with raw FileDescriptor internals`, while those of SDK 34 and lower pass.

Robolectric's runner is a JUnit 4 one, so the test class has to be in `androidHostTest`, not in `commonTest`:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 37])
class MovieDatabaseAndroidTest {

    private val tests = MovieDatabaseTests(ApplicationProvider.getApplicationContext<Context>().toDatabasePath())

    @Test
    fun testInsert() = tests.testInsert()
}
```

### What the SDK Level Chooses

**The SDK level of `@Config` chooses the framework code that runs, not the SQLite version.** Two levels cover the paths
of the framework that SQLlin's Android driver takes: one below 28, as databases open through
`SQLiteDatabase.OpenParams` from API 28 on, and one from 33 on, where the framework reads a database in WAL mode through
several connections. Each level runs every test again, so give more than one level only to the tests that open a
database.

The SQLite is the one of Robolectric's native runtime. With Robolectric 4.17, it is 3.32.2 at every SDK level up to 34,
3.44.3 at 36 and 3.50.6 at 37, while a device of API 24 has SQLite 3.9, and one of API 34 has 3.39. So in a host test:

- A statement that needs a newer SQLite than a device has passes at `sdk = 24`, and fails on that device
- An API that works on a device of API 34, as `DROP_COLUMN`, which needs SQLite 3.35, fails at `sdk = 34`

These versions change with Robolectric's releases, so ask the SQLite which one it is rather than rely on them:

```kotlin
val version = SQLiteDatabase.create(null).use { database ->
    database.rawQuery("select sqlite_version()", null).use { it.moveToFirst(); it.getString(0) }
}
```

Test what depends on the SQLite version, as the APIs marked with `@PlatformDependentSQLiteAPI` do, on an emulator or a
device of the API level in question.

## The Database of a Test

**Keep the path a parameter.** A function that creates the app's `Database` should take its `DatabasePath`, with the
app's own path as the default, so that a test can pass its own, rather than depend on how the app gets its `Context`:

```kotlin
fun createMovieDatabase(path: DatabasePath = databasePath): Database =
    Database(DSLDBConfiguration(name = "movie.db", path = path, version = 3, create = { /* ... */ }))
```

**Close a database before deleting it.** `deleteDatabase(path, name)` of sqllin-driver deletes the database file with
its `-wal`, `-shm` and `-journal` files. Windows can't delete a file that is open, and a database that is still open
keeps its files:

```kotlin
@AfterTest
fun tearDown() {
    database.close()
    deleteDatabase(path, "movie.db")
}
```

On the JVM and in native tests, the file of a test is still there in the next one, so delete it before and after each
test. Robolectric gives each test a data directory of its own, so a host test never finds the file of another one;
closing the database is all it needs.

## Coroutines

`observe { }` and sqllin-dsl-paging's `pagingSource { }` run their queries in `Dispatchers.IO` by default, which works
under `runTest` as it is: a query is real work that the test suspends for, not a delay that virtual time has to skip.
SQLlin's own tests load a `pagingSource` so, through androidx.paging's `TestPager`, and through a `Pager` with
`asSnapshot { }`.

To assert that an observed query emits **nothing**, run its queries in the test's own context, so that `runCurrent()`
lets them catch up:

```kotlin
@Test
fun doesNotEmitForAnotherTable() = runTest {
    val books = Channel<List<Book>>(Channel.UNLIMITED)
    backgroundScope.launch {
        database.observe(EmptyCoroutineContext) { BookTable SELECT X }.collect { books.send(it) }
    }
    assertEquals(emptyList(), books.receive())

    database suspendedScope { PersonTable INSERT Person(name = "Ann", age = 30) }
    runCurrent()
    assertTrue(books.tryReceive().isFailure)
}
```

## Tables of Classes That Only Tests Have

The setup of [Getting Start](getting-start.md) runs sqllin-processor on `commonMain` only, so a `@DBRow` class in a test
source set, as one that keeps an old structure for a migration test, gets no table object. Add the processor to the
test compilation that should have it:

```kotlin
dependencies {
    add("kspCommonMainMetadata", "com.ctrip.kotlin:sqllin-processor:$sqllinVersion")
    add("kspAndroidHostTest", "com.ctrip.kotlin:sqllin-processor:$sqllinVersion")
    add("kspJvmTest", "com.ctrip.kotlin:sqllin-processor:$sqllinVersion")
}
```

A native target has such a configuration too, as `kspIosSimulatorArm64Test`. It generates the table objects of the
classes in that compilation's test source sets, and leaves those of `commonMain` to `kspCommonMainMetadata`.

The class can be in `commonTest` or in the platform's test source set, but **the code that uses its table object has to
be in the platform's test source set**, as `androidHostTest` or `jvmTest`: the object belongs to the platform's
compilation, which the code of `commonTest` can't see, failing with `Unresolved reference`.

## Testing a Migration

Create the database at the old version, close it, open it again with the app's own configuration, and check what the
upgrade left:

```kotlin
// In androidHostTest, or jvmTest: the structure of version 2, which version 3 drops a table of
@DBRow("movie_genres")
@Serializable
data class MovieGenreV2(val movieId: Long, val genreId: Int)

@OptIn(ExperimentalDSLDatabaseAPI::class)
@Test
fun upgradesFrom2To3() {
    // 1. Version 2, created by the test
    Database(
        DSLDBConfiguration(
            name = "movie.db",
            path = path,
            version = 2,
            create = {
                CREATE(MovieTable)
                CREATE(MovieGenreV2Table)
            },
        )
    ).close()

    // 2. The app's own configuration, at version 3, whose `upgrade` runs as the database opens
    val database = createMovieDatabase(path)
    try {
        // 3. What the upgrade left
        lateinit var dropped: PragmaStatement<List<ColumnInfo>>
        database {
            dropped = PRAGMA_TABLE_INFO("movie_genres")
        }
        assertTrue(dropped.getResults().isEmpty())
    } finally {
        database.close()
    }
}
```

- **The old structure** is a `@DBRow` class of the test, under the table's name, whose table object the processor
  generates as the section above describes. Where only the name of a table matters, another table's `withName(name)`
  stands in for it
- **`PRAGMA_TABLE_INFO(name)`** returns the columns of a table, and an empty list for a table that doesn't exist, so it
  checks both what a migration created and what it dropped. Insert rows at the old version and select them after the
  upgrade to check what it kept
- **Check that the test can fail**: remove the step of the migration it tests, and see it fail. A migration test passes
  for nothing when the old structure was never there: `PRAGMA_TABLE_INFO` returns an empty list for a table that the
  test never created, as it does for one that the upgrade dropped
