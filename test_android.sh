#Run Android instrumented tests
./gradlew :sqllin-driver:connectedDebugAndroidTest --stacktrace
./gradlew :sqllin-dsl:connectedDebugAndroidTest --stacktrace
adb uninstall com.ctrip.sqllin.driver.test
adb uninstall com.ctrip.sqllin.dsl.test