package com.ctrip.sqllin.dsl

import kotlinx.serialization.KSerializer

/**
 * Base where clause property in SQL
 * @author yaqiao
 */

public fun interface DBEntity<T> {

    public fun kSerializer(): KSerializer<T>
}