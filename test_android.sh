#Run Android instrumented tests
./gradlew :sqllin-driver:connectedDebugAndroidTest --stacktrace
./gradlew :sqllin-dsl-test:connectedDebugAndroidTest --stacktrace
#adb uninstall com.ctrip.sqllin.driver.test
#adb uninstall com.ctrip.sqllin.dsl.test