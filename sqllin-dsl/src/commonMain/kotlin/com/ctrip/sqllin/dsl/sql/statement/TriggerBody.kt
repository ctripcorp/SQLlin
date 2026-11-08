/*
 * Copyright (C) 2026 Ctrip.com.
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
 * Collects the statements of the body of a trigger, which run when the trigger fires rather than when the scope ends:
 * they become the SQL of `CREATE TRIGGER`.
 *
 * @author Yuang Qiao
 */
internal class TriggerBody : StatementContainer {

    val statements = ArrayDeque<SingleStatement>()

    infix fun addStatement(statement: SingleStatement) {
        statements.add(statement)
    }

    override infix fun removeStatement(statement: SingleStatement) {
        statements.remove(statement)
    }

    override fun replaceStatement(statement: SingleStatement, newStatement: SingleStatement) {
        val index = statements.indexOf(statement)
        check(index >= 0) { "The statement isn't one of the trigger's: a clause was already appended to it, which replaced it, or it became part of another statement, as a subquery does." }
        statements[index] = newStatement
    }
}

/**
 * `SELECT RAISE(...)`, which only a trigger runs: it stops the statement that fired the trigger, or skips its row.
 */
internal class RaiseStatement(sqlStr: String) : SingleStatement(sqlStr) {
    override val parameters: MutableList<Any?>? = null
    override fun execute(): Unit = error("RAISE only runs in a trigger.")
}
