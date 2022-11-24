package com.ctrip.sqllin.driver

/**
 * SQL statement that used for unit test.
 * @author yaqiao
 */

object SQL {

    const val DATABASE_NAME = "BookStore.db"

    const val CREATE_BOOK = "create table book (id integer primary key autoincrement, name text, author text, pages integer, price real)"

    const val CREATE_CATEGORY = "create table Category (id integer primary key autoincrement, category_name text, category_code integer)"

    const val ASSOCIATE = "alter table Book add column category_id integer"

    const val INSERT_BOOK = "insert into Book (name, author, pages, price) values (?, ?, ?, ?)"

    const val QUERY_BOOK = "select * from Book"

    const val UPDATE_BOOK = "update Book set price = ? where name = ?"

    const val DELETE_BOOK = "delete from Book where pages > ?"
}
