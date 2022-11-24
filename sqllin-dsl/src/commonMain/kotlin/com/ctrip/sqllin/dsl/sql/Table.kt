package com.ctrip.sqllin.dsl.sql

import com.ctrip.sqllin.dsl.DBEntity

/**
 * SQL table
 * @author yaqiao
 */

abstract class Table<T : DBEntity<T>>(
    internal val tableName: String,
)