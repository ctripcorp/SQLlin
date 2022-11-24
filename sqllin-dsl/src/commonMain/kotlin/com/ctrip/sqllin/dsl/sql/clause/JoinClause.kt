package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.Table

/**
 * SQL "join" clause.
 * @author yaqiao
 */

/*sealed class JoinClause<T : DBEntity<T>>(private val table: Table<T>) : SelectClause<T> {

    abstract val joinClauseName: String

    final override val clauseStr: String
        get() = " $joinClauseName ${table.tableName} "
}

class JoinSelectStatementWithoutCondition<T : DBEntity<T>> internal constructor(private val joinClause: JoinClause<T>) {

}

object CROSS
class CrossJoinClause<T : DBEntity<T>>(table: Table<T>) : JoinClause<T>(table) {
    override val joinClauseName: String = "CROSS JOIN"

    infix fun JOIN(table: Table<T>): JoinSelectStatement<T> = JoinSelectStatement()
}

object INNER
class InnerJoinClause<T : DBEntity<T>>(table: Table<T>) : JoinClause<T>(table) {
    override val joinClauseName: String = "INNER JOIN"

    infix fun JOIN(table: Table<T>): JoinSelectStatementWithoutCondition<T> =
        JoinSelectStatementWithoutCondition(this)
}

object LEFT_OUTER
class LeftOuterJoinClause<T : DBEntity<T>>(table: Table<T>) : JoinClause<T>(table) {
    override val joinClauseName: String = "LEFT OUTER JOIN"

    infix fun JOIN(table: Table<T>): JoinSelectStatementWithoutCondition<T> =
        JoinSelectStatementWithoutCondition(this)
}

infix fun <T : DBEntity<T>> InnerJoinClause<T>.JOIN(table: Table<T>)*/