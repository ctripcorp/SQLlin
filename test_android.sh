#Run Android instrumented tests
./gradlew :sqllin-driver-test:connectedAndroidDeviceTest --stacktrace
./gradlew :sqllin-dsl-test:connectedAndroidDeviceTest --stacktrace
#adb uninstall com.ctrip.sqllin.driver.test
#adb uninstall com.ctrip.sqllin.dsl.test