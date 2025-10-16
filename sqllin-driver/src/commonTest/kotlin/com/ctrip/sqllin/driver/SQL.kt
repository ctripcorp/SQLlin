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

package com.ctrip.sqllin.driver

/**
 * SQL statement that used for unit test.
 * @author Yuang Qiao
 */

object SQL {

    const val DATABASE_NAME = "BookStore.db"

    const val CREATE_BOOK = "create table book (id integer primary key autoincrement, name text, author text, pages integer, price real, array blob)"

    const val CREATE_CATEGORY = "create table Category (id integer primary key autoincrement, category_name text, category_code integer)"

    const val ASSOCIATE = "alter table Book add column category_id integer"

    const val INSERT_BOOK = "insert into Book (name, author, pages, price, array) values (?, ?, ?, ?, ?)"

    const val QUERY_BOOK = "select * from Book"

    const val UPDATE_BOOK = "update Book set price = ? where name = ?"

    const val DELETE_BOOK = "delete from Book where pages > ?"
}
