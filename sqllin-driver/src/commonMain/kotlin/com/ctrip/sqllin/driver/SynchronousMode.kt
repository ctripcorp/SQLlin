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

/**
 * SQLite synchronous modes controlling disk write behavior.
 *
 * Determines how aggressively SQLite flushes data to disk.
 *
 * @property OFF No syncing - fastest but risks database corruption on power loss
 * @property NORMAL Sync at critical moments - good balance of safety and performance
 * @property FULL Sync after each transaction - safest but slower
 * @property EXTRA Most paranoid mode with additional syncing
 *
 * @author Yuang Qiao
 */
public enum class SynchronousMode(internal val value: Int) {
    OFF(0), NORMAL(1), FULL(2), EXTRA(3);
}