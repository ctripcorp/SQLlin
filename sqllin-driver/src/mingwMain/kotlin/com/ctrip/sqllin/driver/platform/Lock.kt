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

import kotlinx.cinterop.Arena
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import platform.posix.PTHREAD_MUTEX_RECURSIVE
import platform.posix.pthread_mutex_destroy
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_tVar
import platform.posix.pthread_mutex_trylock
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutexattr_destroy
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_tVar

/**
 * MinGW/Windows platform lock implementation using pthread recursive mutex.
 *
 * @author Yuang Qiao
 */
@OptIn(ExperimentalForeignApi::class)
internal actual class Lock actual constructor() {

    private val arena = Arena()
    private val attr = arena.alloc<pthread_mutexattr_tVar>()
    private val mutex = arena.alloc<pthread_mutex_tVar>()

    init {
        pthread_mutexattr_init(attr.ptr)
        pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_RECURSIVE)
        pthread_mutex_init(mutex.ptr, attr.ptr)
    }

    actual fun lock() {
        pthread_mutex_lock(mutex.ptr)
    }

    actual fun unlock() {
        pthread_mutex_unlock(mutex.ptr)
    }

    actual fun tryLock(): Boolean = pthread_mutex_trylock(mutex.ptr) == 0

    actual fun close() {
        pthread_mutex_destroy(mutex.ptr)
        pthread_mutexattr_destroy(attr.ptr)
        arena.clear()
    }
}
