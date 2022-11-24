package com.ctrip.sqllin.dsl.sql.statement

/**
 * Collect and execute all SQL statement in 'database {}' block.
 * @author yaqiao
 */

internal class DatabaseExecuteEngine : StatementContainer {

    private var statementLinkedList: StatementLinkedList<ExecutableStatement>? = null

    override infix fun changeLastStatement(statement: SingleStatement) {
        if (statementLinkedList?.lastStatement is UpdateStatementWithoutWhereClause<*>
            || statementLinkedList?.lastStatement is SelectStatement<*>)
            statementLinkedList!!.resetLastStatement(statement)
        else
            throw IllegalStateException("Current statement can't append clause.")
    }

    infix fun addStatement(statement: ExecutableStatement) {
        if (statementLinkedList != null)
            statementLinkedList!!.addStatement(statement)
        else
            statementLinkedList = StatementLinkedList(statement)
    }

    fun executeAllStatement() = statementLinkedList?.run {
        forEach {
            when (it) {
                is SingleStatement -> {
                    println("SQL String: ${it.sqlStr}")
                    it.execute()
                }
                is TransactionStatementsGroup -> it.execute()
            }
        }
        statementLinkedList = null
    } ?: Unit
}