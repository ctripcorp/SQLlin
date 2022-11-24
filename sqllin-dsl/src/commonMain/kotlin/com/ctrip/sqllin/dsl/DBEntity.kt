package com.ctrip.sqllin.dsl

import kotlinx.serialization.KSerializer

/**
 * Base where clause property in SQL
 * @author yaqiao
 */

fun interface DBEntity<T> {

    fun kSerializer(): KSerializer<T>
}