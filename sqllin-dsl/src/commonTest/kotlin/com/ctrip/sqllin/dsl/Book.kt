package com.ctrip.sqllin.dsl

import com.ctrip.sqllin.dsl.annotation.DBRow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Book entity
 * @author yaqiao
 */

@DBRow("book")
@Serializable
data class Book(
    val name: String,
    val author: String,
    val price: Double,
    val pages: Int,
) : DBEntity<Book> {
    override fun kSerializer(): KSerializer<Book> = serializer()
}
