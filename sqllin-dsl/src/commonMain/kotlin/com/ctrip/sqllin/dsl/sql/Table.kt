package com.ctrip.sqllin.dsl.sql

import com.ctrip.sqllin.dsl.DBEntity

/**
 * SQL table
 * @author yaqiao
 */

public abstract class Table<T : DBEntity<T>>(
    internal val tableName: String,
)