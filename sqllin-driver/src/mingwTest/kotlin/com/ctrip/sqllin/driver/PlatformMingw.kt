/*
 * Copyright (C) 2022 Ctrip.com.
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

package com.ctrip.sqllin.driver

import kotlinx.cinterop.*
import platform.posix._getcwd

/**
 * Windows platform-related functions
 * The doc of _getcwd: https://learn.microsoft.com/en-us/cpp/c-runtime-library/reference/getcwd-wgetcwd?view=msvc-170
 * @author yaqiao
 */

actual fun getPlatformStringPath(): String =
    _getcwd(null, 0)?.toKString() ?: ""