package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.driver.withTransaction

/**
 * The group of some statements those them in same transaction.
 * @author yaqiao
 */

internal class TransactionStatementsGroup(
    private val databaseConnection: DatabaseConnection,
) : ExecutableStatement, StatementContainer {

    private lateinit var statementList: StatementLinkedList<SingleStatement>

    infix fun addStatement(statement: SingleStatement) {
        if (this::statementList.isInitialized)
            statementList.addStatement(statement)
        else
            statementList = StatementLinkedList(statement)
    }

    override fun execute() = databaseConnection.withTransaction {
        statementList.forEach {
            println("SQL String: ${it.sqlStr}")
            it.execute()
        }
    }

    override infix fun changeLastStatement(statement: SingleStatement) {
        if (statementList.lastStatement is UpdateStatementWithoutWhereClause<*>
            || statementList.lastStatement is SelectStatement<*>)
            statementList resetLastStatement statement
        else
            throw IllegalStateException("Current statement can't append clause.")
    }
}