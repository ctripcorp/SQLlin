#Run Android instrumented tests
adb uninstall com.ctrip.sqllin.driver.test
adb uninstall com.ctrip.sqllin.dsl.test
./gradlew :sqllin-driver:connectedDebugAndroidTest --stacktrace
./gradlew :sqllin-dsl:connectedDebugAndroidTest --stacktrace