/*
 * Copyright (C) 2022 Ctrip.com.
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
package com.ctrip.sqllin.dsl.sql.statement

/**
 * Single LinkedList that used for store 'CompleteStatement'
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