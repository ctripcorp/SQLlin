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

/**
 * Platform-specific reentrant lock interface.
 *
 * Implementations must support reentrant locking.
 *
 * @author Yuang Qiao
 */
internal expect class Lock() {
    /**
     * Acquires the lock.
     */
    fun lock()

    /**
     * Releases the lock.
     */
    fun unlock()

    /**
     * Attempts to acquire the lock without blocking.
     *
     * @return `true` if the lock was acquired, `false` otherwise
     */
    fun tryLock(): Boolean

    /**
     * Closes and releases any resources associated with this lock.
     */
    fun close()
}

/**
 * Executes a block while holding the lock.
 */
internal inline fun <T> Lock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}