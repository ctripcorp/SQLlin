/*
 * Copyright (C) 2026 Ctrip.com.
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
 * How a transaction begins, as `BEGIN DEFERRED`, `BEGIN IMMEDIATE` or `BEGIN EXCLUSIVE`, which decides when it takes the
 * database's write lock.
 *
 * @property DEFERRED Takes no lock until the transaction reads, nor the write lock until it writes, so other connections
 * can write until then. But a transaction that reads before it writes then fails at once with "database is locked" while
 * another connection writes, as SQLite doesn't wait for the busy timeout where waiting could deadlock. Android's framework
 * can't begin a deferred transaction that writes, so on Android, it begins an IMMEDIATE one
 * @property IMMEDIATE Takes the write lock at once, waiting for the busy timeout while another connection holds it, so
 * that the transaction can't fail later for a lock. The mode of a transaction unless another is given
 * @property EXCLUSIVE Takes the write lock at once too, and in DELETE journal mode also keeps other connections from
 * reading until the transaction ends; in WAL mode, it is the same as IMMEDIATE
 *
 * @author Yuang Qiao
 */
public enum class TransactionMode {
    DEFERRED,
    IMMEDIATE,
    EXCLUSIVE;
}
