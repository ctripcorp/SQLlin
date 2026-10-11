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

package com.ctrip.sqllin.dsl.annotation

/**
 * Makes the table of a `@DBRow` class an FTS4 full-text search table: `CREATE VIRTUAL TABLE name USING fts4(...)`.
 *
 * The properties of the class are the columns, and have to be `String` or `String?`, as FTS indexes text. The class
 * can also have a `Long` or `Long?` property named `rowid` or `docid`, annotated with `@PrimaryKey`, which reads and
 * writes the row's id rather than being a column; `Long?` lets SQLite assign it. The other column annotations, such as
 * `@Unique`, don't apply to an FTS table.
 *
 * An FTS table is searched with `MATCH`, and its matches described with `snippet`, `offsets` and `matchinfo`. FTS4 is in
 * the SQLite of every platform SQLlin supports, Android's included. It has no built-in ranking: `matchinfo` gives the
 * statistics to compute one from.
 *
 * Example:
 * ```kotlin
 * @OptIn(ExperimentalDSLDatabaseAPI::class)
 * @DBRow("articles")
 * @Fts4(tokenizer = FtsTokenizer.PORTER, prefix = [2])
 * @Serializable
 * data class Article(@PrimaryKey val rowid: Long?, val title: String, val body: String)
 * ```
 *
 * @property tokenizer The tokenizer that splits the text into terms
 * @property tokenizerArgs Arguments for the tokenizer, such as `"remove_diacritics=2"` for [FtsTokenizer.UNICODE61]
 * @property prefix The lengths of the prefixes to index, which make prefix queries such as `kot*` faster
 * @property notIndexed The columns whose values are stored but not indexed, so a query doesn't find them
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class Fts4(
    val tokenizer: FtsTokenizer = FtsTokenizer.SIMPLE,
    val tokenizerArgs: Array<String> = [],
    val prefix: IntArray = [],
    val notIndexed: Array<String> = [],
)

/**
 * Makes the table of a `@DBRow` class an FTS3 full-text search table: `CREATE VIRTUAL TABLE name USING fts3(...)`.
 *
 * FTS3 is the older version of FTS4, which it reads the same way, without FTS4's options and statistics. Prefer
 * [Fts4], unless the table has to be FTS3, such as one an earlier version of an app created. The rules for the
 * properties are those of [Fts4].
 *
 * @property tokenizer The tokenizer that splits the text into terms
 * @property tokenizerArgs Arguments for the tokenizer, such as `"remove_diacritics=2"` for [FtsTokenizer.UNICODE61]
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class Fts3(
    val tokenizer: FtsTokenizer = FtsTokenizer.SIMPLE,
    val tokenizerArgs: Array<String> = [],
)

/**
 * A tokenizer of FTS3 and FTS4, which splits text into terms.
 *
 * @property sqlName Its name in `CREATE VIRTUAL TABLE`
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public enum class FtsTokenizer(public val sqlName: String) {
    /** Splits on spaces and punctuation and folds ASCII to lower case. */
    SIMPLE("simple"),
    /** [SIMPLE], then reduces English words to their stems, so that "running" matches "run". */
    PORTER("porter"),
    /** Splits by the Unicode properties of the characters and folds them to lower case, removing diacritics by default. */
    UNICODE61("unicode61"),
}
