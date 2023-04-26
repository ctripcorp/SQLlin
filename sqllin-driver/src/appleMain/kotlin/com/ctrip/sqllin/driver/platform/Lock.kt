/*
 * Copyright (C) 2023 Ctrip.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctrip.sqllin.driver.platform

import platform.Foundation.NSRecursiveLock

/**
 * A simple lock implementation in Apple platforms.
 * Implementations of this class should be re-entrant.
 * @author yaqiao
 */

internal actual class Lock actual constructor() {

    private val nsRecursiveLock = NSRecursiveLock()

    actual fun lock() = nsRecursiveLock.lock()

    actual fun unlock() = nsRecursiveLock.unlock()

    actual fun tryLock(): Boolean = nsRecursiveLock.tryLock()

    actual fun close() = Unit
}