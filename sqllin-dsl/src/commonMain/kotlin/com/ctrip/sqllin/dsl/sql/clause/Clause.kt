package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity

/**
 * Abstract clause, include 'where', 'update set' and more.
 * @author yaqiao
 */

sealed interface Clause<T : DBEntity<T>>