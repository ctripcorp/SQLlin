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
package com.ctrip.sqllin.sample

import com.ctrip.sqllin.dsl.Database
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.clause.OrderByWay.DESC
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Sample
 * @author yaqiao
 */

class Sample(path: DatabasePath) {

    private val db by lazy { Database(name = "Person.db", path = path, version = 1) }

    fun sample() {
        val tom = Person(age = 4, name = "Tom")
        val jerry = Person(age = 3, name = "Jerry")
        val jack = Person(age = 8, name = "Jack")

        lateinit var selectStatement: SelectStatement<Person>
        db {
            transaction {
                PersonTable { table ->
                    table INSERT listOf(tom, jerry, jack)
                    table UPDATE SET { age = 5; name = "Tom" } WHERE ((age LTE 5) AND (name NEQ "Tom"))
                    table DELETE WHERE ((age GTE 10) OR (name NEQ "Jerry"))
                }
            }
            PersonTable { table ->
                selectStatement = table SELECT WHERE (age LTE 5) GROUP_BY age HAVING (upper(name) EQ "TOM") ORDER_BY (age to DESC) LIMIT 2 OFFSET 1
            }
        }
        selectStatement.getResults().forEach {
            println(it)
        }

    }

    fun joinSample() {
        db {
            PersonTable { table ->
                table SELECT CROSS_JOIN<Student>(TranscriptTable)
                table SELECT INNER_JOIN<Student>(TranscriptTable) USING name
                table SELECT NATURAL_JOIN<Student>(TranscriptTable)
                table SELECT LEFT_OUTER_JOIN<Student>(TranscriptTable) USING name
                table SELECT NATURAL_LEFT_OUTER_JOIN<Student>(TranscriptTable)
            }
        }
    }

    fun onDestroy() {
        db.close()
    }
}

@DBRow("person")
@Serializable
data class Person(
    val age: Int?,
    val name: String?,
) : DBEntity<Person> {
    override fun kSerializer(): KSerializer<Person> = serializer()
}

@DBRow("transcript")
@Serializable
data class Transcript(
    val name: String?,
    val math: Int,
    val english: Int,
) : DBEntity<Transcript> {
    override fun kSerializer(): KSerializer<Transcript> = serializer()
}

@Serializable
data class Student(
    val name: String?,
    val age: Int?,
    val math: Int,
    val english: Int,
) : DBEntity<Student> {
    override fun kSerializer(): KSerializer<Student> = serializer()
}