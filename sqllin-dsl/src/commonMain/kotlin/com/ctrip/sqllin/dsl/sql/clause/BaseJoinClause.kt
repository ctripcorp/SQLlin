package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.statement.JoinSelectStatement
import com.ctrip.sqllin.dsl.sql.statement.JoinStatementWithoutCondition

/**
 * SQL abstract "JOIN" clause
 * @author yaqiao
 */

sealed class BaseJoinClause<R : DBEntity<R>>(private vararg val tables: Table<*>) : SelectClause<R> {

    abstract val clauseName: String

    final override val clauseStr: String
        get() = buildString {
            append(clauseName)
            tables.forEachIndexed { index, table ->
                append(table.tableName)
                if (index < tables.lastIndex)
                    append(',')
            }
        }
}

sealed class NaturalJoinClause<R : DBEntity<R>>(vararg tables: Table<*>) : BaseJoinClause<R>(*tables)

sealed class JoinClause<R : DBEntity<R>>(vararg tables: Table<*>) : BaseJoinClause<R>(*tables)

//infix fun <R : DBEntity<R>> JoinStatementWithoutCondition<R>.ON(condition: SelectCondition): JoinSelectStatement<R> =
    //convertToJoinSelectStatement(condition)

inline infix fun <R : DBEntity<R>> JoinStatementWithoutCondition<R>.USING(clauseElement: ClauseElement): JoinSelectStatement<R> =
    USING(listOf(clauseElement))

infix fun <R : DBEntity<R>> JoinStatementWithoutCondition<R>.USING(clauseElements: Iterable<ClauseElement>): JoinSelectStatement<R> =
    convertToJoinSelectStatement(clauseElements)