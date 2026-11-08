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

import com.ctrip.sqllin.dsl.DSLDBConfiguration
import com.ctrip.sqllin.dsl.Database
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.sql.X
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.clause.OrderByWay.DESC
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Sample
 * @author Yuang Qiao
 */

object Sample {

    private val db by lazy {
        Database(
            DSLDBConfiguration(
                name = "Person.db",
                path = databasePath,
                version = 1,
                create = {
                    PersonTable {
                        CREATE()
                    }
                    this CREATE TranscriptTable
                },
                upgrade = { _, _ -> }
            ),
            enableSimpleSQLLog = true,
        )
    }

    fun sample() {
        val tom = Person(id = 0, age = 4, name = "Tom")
        val jerry = Person(id = 1, age = 3, name = "Jerry")
        val jack = Person(id = 2, age = 8, name = "Jack")

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

    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun joinSample() {
        db {
            (PersonTable INNER_JOIN TranscriptTable USING PersonTable.name) SELECT X<Student>()
            (PersonTable NATURAL_JOIN TranscriptTable) SELECT X<Student>()
        }
    }

    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun concurrentSafeCall() {
        CoroutineScope(Dispatchers.Default).launch {
            db suspendedScope {
                (PersonTable INNER_JOIN TranscriptTable USING PersonTable.name) SELECT X<Student>()
                delay(100)
                (PersonTable NATURAL_JOIN TranscriptTable) SELECT X<Student>()
            }
        }
    }

    fun onDestroy() {
        db.close()
    }
}

typealias MyInt = Int

@DBRow("person")
@Serializable
data class Person(
    @PrimaryKey val id: Long?,
    val age: MyInt?,
    val name: String?,
)

@DBRow("transcript")
@Serializable
data class Transcript(
    @PrimaryKey val id: Long?,
    val name: String?,
    val math: Int,
    val english: Int,
)

@Serializable
data class Student(
    val name: String?,
    val age: MyInt?,
    val math: Int,
    val english: Int,
)