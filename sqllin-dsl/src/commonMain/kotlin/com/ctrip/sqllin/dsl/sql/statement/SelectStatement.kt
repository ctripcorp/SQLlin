package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.driver.withQuery
import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.compiler.QueryDecoder
import kotlinx.serialization.DeserializationStrategy
import kotlin.jvm.Volatile

/**
 * Select statement
 * @author yaqiao
 */

public sealed class SelectStatement<T : DBEntity<T>>(
    sqlStr: String,
    internal val deserializer: DeserializationStrategy<T>,
    internal val connection: DatabaseConnection,
    internal val container: StatementContainer,
) : SingleStatement(sqlStr) {

    @Volatile
    private var result: List<T>? = null

    final override fun execute() {
        result = connection.withQuery(sqlStr) {
            val decoder = QueryDecoder(it)
            buildList {
                it.forEachRow {
                    add(decoder.decodeSerializableValue(deserializer))
                }
            }
        }
    }

    public fun getResults(): List<T> =
        result ?: throw IllegalStateException("You have to call 'execute' function before call 'getResults'!!!")

    protected fun buildSQL(clause: SelectClause<T>): String = buildString {
        append(sqlStr)
        append(clause.clauseStr)
    }

    internal fun <R : DBEntity<R>, S : DBEntity<S>> crossJoin(
        table: Table<R>,
        newDeserializer: DeserializationStrategy<S>,
    ): FinalSelectStatement<S> {
        val sql = buildString {
            append(sqlStr)
            append(" CROSS JOIN ")
            append(table.tableName)
        }
        return FinalSelectStatement(sql, newDeserializer, connection, container)
    }
}

public class WhereSelectStatement<T : DBEntity<T>> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
) : SelectStatement<T>(sqlStr, deserializer, connection, container) {

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container)

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container)

    internal infix fun appendToGroupBy(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        GroupBySelectStatement(buildSQL(clause), deserializer, connection, container)
}

public class JoinSelectStatement<T : DBEntity<T>> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
) : SelectStatement<T>(sqlStr, deserializer, connection, container) {

    internal infix fun appendToWhere(clause: WhereClause<T>): WhereSelectStatement<T> =
        WhereSelectStatement(buildSQL(clause), deserializer, connection, container)

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container)

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container)

    internal infix fun appendToGroupBy(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        GroupBySelectStatement(buildSQL(clause), deserializer, connection, container)
}

public class GroupBySelectStatement<T : DBEntity<T>> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
) : SelectStatement<T>(sqlStr, deserializer, connection, container) {

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container)

    internal infix fun appendToHaving(clause: HavingClause<T>): HavingSelectStatement<T> =
        HavingSelectStatement(buildSQL(clause), deserializer, connection, container)
}

public class HavingSelectStatement<T : DBEntity<T>> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
) : SelectStatement<T>(sqlStr, deserializer, connection, container) {

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container)

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container)
}

public class OrderBySelectStatement<T : DBEntity<T>> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
) : SelectStatement<T>(sqlStr, deserializer, connection, container) {

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container)
}

public class LimitSelectStatement<T : DBEntity<T>> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
) : SelectStatement<T>(sqlStr, deserializer, connection, container) {

    internal infix fun appendToFinal(clause: OffsetClause<T>): FinalSelectStatement<T> =
        FinalSelectStatement(buildSQL(clause), deserializer, connection, container)
}

public class FinalSelectStatement<T : DBEntity<T>> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
) : SelectStatement<T>(sqlStr, deserializer, connection, container)