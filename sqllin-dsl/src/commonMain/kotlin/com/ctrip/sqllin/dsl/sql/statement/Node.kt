package com.ctrip.sqllin.dsl.sql.statement

/**
 * A sample data struct that has two pointers.
 * @author yaqiao
 */

internal class Node<T>(
    val element: T,
    var pre: Node<T>? = null,
    var next: Node<T>? = null,
)