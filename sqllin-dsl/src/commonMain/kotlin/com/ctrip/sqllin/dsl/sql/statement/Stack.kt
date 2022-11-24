package com.ctrip.sqllin.dsl.sql.statement

import kotlin.jvm.Volatile

/**
 * A sample Stack implementation.
 * @author yaqiao
 */

internal class Stack<T> {

    @Volatile
    private var topNode: Node<T>? = null

    val isEmpty
        get() = topNode == null

    val isNotEmpty
        get() = !isEmpty

    val top: T?
        get() = topNode?.element

    fun pop(): T? {
        var value: T? = null
        topNode = topNode?.let {
            value = it.element
            val newTopNode = it.next?.apply { pre = null }
            it.next = null
            newTopNode
        }
        return value
    }

    fun push(e: T) {
        val newNode = Node(e)
        if (isEmpty)
            topNode = newNode
        else {
            topNode!!.pre = newNode
            newNode.next = topNode
            topNode = newNode
        }
    }
}