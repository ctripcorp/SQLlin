#Run Android instrumented tests
adb shell pm clear com.ctrip.sqllin.driver.test
adb shell pm clear com.ctrip.sqllin.dsl.test
./gradlew :sqllin-driver:connectedDebugAndroidTest --stacktrace
./gradlew :sqllin-dsl:connectedDebugAndroidTest --stacktrace