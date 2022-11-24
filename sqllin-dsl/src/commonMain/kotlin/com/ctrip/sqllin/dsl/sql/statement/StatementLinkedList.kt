package com.ctrip.sqllin.dsl.sql.statement

/**
 * Single LinkedList that used for store 'CompleteStatement'.
 * @author yaqiao
 */

internal class StatementLinkedList<T : ExecutableStatement>(firstStatement: T) : Iterator<T> {

    private var firstNode = Node(firstStatement)
    private var lastNode = firstNode

    val lastStatement: T
        get() = lastNode.element

    infix fun addStatement(statement: T) {
        val node = Node(statement)
        lastNode.next = node
        node.pre = lastNode
        lastNode = node
    }

    private var forEachNode: Node<T>? = firstNode

    override fun hasNext(): Boolean = forEachNode != null

    override fun next(): T = forEachNode?.apply {
        forEachNode = next
    }?.element ?: throw IllegalStateException("Engine must not be empty!!")

    infix fun resetLastStatement(statement: T) {
        val secondLastNode = lastNode.pre
        val isOnlyOneNode = firstNode === lastNode
        lastNode.pre = null
        lastNode = Node(statement)
        if (isOnlyOneNode) {
            firstNode = lastNode
            forEachNode = lastNode
        }
        secondLastNode?.run {
            next = lastNode
            lastNode.pre = this
        }
    }
}