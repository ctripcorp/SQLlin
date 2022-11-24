package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.dsl.DBEntity

/**
 * Used for compose multi select statement that use the 'UNION' clause
 * @author yaqiao
 */

internal class UnionSelectStatementGroup<T : DBEntity<T>> : StatementContainer {

    private var statementLinkedList: StatementLinkedList<SelectStatement<T>>? = null

    infix fun addSelectStatement(selectStatement: SelectStatement<T>) {
        if (statementLinkedList != null)
            statementLinkedList!!.addStatement(selectStatement)
        else
            statementLinkedList = StatementLinkedList(selectStatement)
    }

    internal fun unionStatements(isUnionAll: Boolean): FinalSelectStatement<T> {
        require(statementLinkedList?.hasNext() == true) { "Please write at least two 'select' statements on 'UNION' scope" }
        var firstStatement: SelectStatement<T>? = null
        val unionSqlStr = buildString {
            statementLinkedList!!.run {
                val unionKeyWord = if (isUnionAll) " UNION ALL " else " UNION "
                do {
                    val next = next()
                    append(next.sqlStr)
                    val hasNext = hasNext()
                    if (firstStatement == null) {
                        firstStatement = next
                        if (!hasNext)
                            throw IllegalStateException("Please write at least two 'select' statements on 'UNION' scope")
                    }
                    if (hasNext)
                        append(unionKeyWord)
                } while (hasNext)
            }
        }

        return firstStatement!!.run {
            FinalSelectStatement(
                sqlStr = unionSqlStr,
                deserializer = deserializer,
                connection = connection,
                container = container,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun changeLastStatement(statement: SingleStatement) {
        if (statementLinkedList?.lastStatement is SelectStatement<*>)
            statementLinkedList!!.resetLastStatement(statement as SelectStatement<T>)
        else
            throw IllegalStateException("Current statement can't append clause")
    }
}