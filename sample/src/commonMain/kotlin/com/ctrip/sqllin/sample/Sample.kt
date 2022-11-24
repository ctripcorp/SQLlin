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


    private val tablePerson = "Book"
    private val tableTranscript = "Transcript"

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
): DBEntity<Transcript> {
    override fun kSerializer(): KSerializer<Transcript> = serializer()
}

@Serializable
data class Student(
    val name: String?,
    val age: Int?,
    val math: Int,
    val english: Int,
): DBEntity<Student> {
    override fun kSerializer(): KSerializer<Student> = serializer()
}