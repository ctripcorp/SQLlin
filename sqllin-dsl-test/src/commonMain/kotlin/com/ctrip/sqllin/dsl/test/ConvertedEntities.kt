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

@file:OptIn(ExperimentalUuidApi::class)

package com.ctrip.sqllin.dsl.test

import com.ctrip.sqllin.dsl.annotation.CollateNoCase
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.DBView
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * The entities of the columns whose serializers convert their values, which CommonConvertedTest tests.
 */

/**
 * Stores an instant as the number of its milliseconds, which keeps the order of instants, unlike the ISO-8601 string
 * of the built-in serializer.
 */
object EpochMillisSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("com.ctrip.sqllin.dsl.test.EpochMillis", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilliseconds())
    override fun deserialize(decoder: Decoder): Instant = Instant.fromEpochMilliseconds(decoder.decodeLong())
}

/**
 * Stores a string reversed, as a serializer that encodes or encrypts a string stores another one, so that a condition
 * only matches if it reverses the string it compares with too.
 */
class ReversedStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("com.ctrip.sqllin.dsl.test.ReversedString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value.reversed())
    override fun deserialize(decoder: Decoder): String = decoder.decodeString().reversed()
}

/** An instant stored as the number of its milliseconds, as the use of its type in this type alias declares. */
typealias EpochInstant = @Serializable(with = EpochMillisSerializer::class) Instant

/** A value class of a number, stored as the number. */
@JvmInline
@Serializable
value class Score(val value: Int)

/** A value class of a string, stored as the string. */
@JvmInline
@Serializable
value class Email(val value: String)

/** A value class of bytes, stored as a BLOB. */
@JvmInline
@Serializable
value class Digest(val bytes: ByteArray)

/**
 * A row of every kind of converted column: a `Uuid` primary key, stored as its string; instants stored as numbers by a
 * serializer given on the property, and on the type alias of the property's type; an instant and a duration stored by
 * their built-in serializers, as ISO-8601 strings; value classes, nullable or not, one of them with a collation; and a
 * `String` with a serializer of its own.
 */
@Serializable
@DBRow("converted_event")
data class ConvertedEvent(
    @PrimaryKey val id: Uuid,
    @Serializable(with = EpochMillisSerializer::class) val startsAt: Instant,
    @Serializable(with = EpochMillisSerializer::class) val endsAt: Instant?,
    val loggedAt: EpochInstant,
    val createdAt: Instant,
    val duration: Duration?,
    val score: Score,
    val bonus: Score?,
    @CollateNoCase val email: Email,
    val digest: Digest,
    @Serializable(with = ReversedStringSerializer::class) val code: String,
)

/**
 * The instants of the [ConvertedEvent]s, as a SELECT of them, and the view of them, read.
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBView("converted_event_start")
@Serializable
data class ConvertedEventStart(
    @Serializable(with = EpochMillisSerializer::class) val startsAt: Instant,
    val score: Score,
)

/**
 * The latest instant of the [ConvertedEvent]s, which `max` selects, and is NULL without any.
 */
@Serializable
data class LatestStart(@Serializable(with = EpochMillisSerializer::class) val startsAt: Instant?)

/**
 * The instants of another table, which a subquery of [ConvertedEvent]s compares with.
 */
@Serializable
@DBRow("converted_holiday")
data class ConvertedHoliday(
    @PrimaryKey val id: Long?,
    @Serializable(with = EpochMillisSerializer::class) val startsAt: Instant,
)

/**
 * The severity of a [NamedEnumRow], which the serializer its class declares stores as its name, in a column of text,
 * rather than as its ordinal.
 */
@Serializable(with = SeverityByNameSerializer::class)
enum class Severity { LOW, MEDIUM, HIGH }

object SeverityByNameSerializer : KSerializer<Severity> {
    override val descriptor = PrimitiveSerialDescriptor("com.ctrip.sqllin.dsl.test.SeverityByName", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Severity) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): Severity = Severity.valueOf(decoder.decodeString())
}

/**
 * Stores a [UserStatus] by its name, as the serializer given on a property of that enum, which has none of its own.
 */
object UserStatusByNameSerializer : KSerializer<UserStatus> {
    override val descriptor = PrimitiveSerialDescriptor("com.ctrip.sqllin.dsl.test.UserStatusByName", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UserStatus) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): UserStatus = UserStatus.valueOf(decoder.decodeString())
}

/**
 * Enums stored by their names, with the serializer of the enum class and with that of the property, which were stored
 * as their names but compared and set as their ordinals before 2.6.0; one stored as its ordinal; and one serialized
 * contextually, which SQLlin, without a `SerializersModule`, stores as its ordinal too.
 */
@Serializable
@DBRow("named_enum_row")
data class NamedEnumRow(
    @PrimaryKey val id: Long?,
    val severity: Severity,
    @Serializable(with = UserStatusByNameSerializer::class) val status: UserStatus?,
    val ordinalStatus: UserStatus,
    @Contextual val contextualStatus: UserStatus,
)

/**
 * Stores a list of tags as one string, the tags separated by commas, as a serializer would store a list as JSON.
 */
object TagsAsTextSerializer : KSerializer<List<String>> {
    override val descriptor = PrimitiveSerialDescriptor("com.ctrip.sqllin.dsl.test.TagsAsText", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: List<String>) = encoder.encodeString(value.joinToString(","))
    override fun deserialize(decoder: Decoder): List<String> = decoder.decodeString().let { if (it.isEmpty()) emptyList() else it.split(',') }
}

/**
 * A list, of a generic type, stored as one string by the serializer of its property.
 */
@Serializable
@DBRow("converted_note")
data class ConvertedNote(
    @PrimaryKey val id: Long?,
    @Serializable(with = TagsAsTextSerializer::class) val tags: List<String>,
)

/** A point, which its plugin-generated serializer encodes as a structure. */
@Serializable
data class Point(val x: Int, val y: Int)

/** Encodes a [Point] as a structure, which no column can hold, as a serializer given on a property can. */
object PointAsStructureSerializer : KSerializer<Point> by Point.serializer()

/**
 * A column that can't be: a point its serializer encodes as a structure, so that its table can't be created, nor its
 * conditions built.
 */
@Serializable
@DBRow("structured_column")
data class StructuredColumn(
    @PrimaryKey val id: Long?,
    @Serializable(with = PointAsStructureSerializer::class) val point: Point,
)

/**
 * A column that can't be: a collation of a number, which only a column of text can have, so that its table can't be
 * created.
 */
@Serializable
@DBRow("collated_number")
data class CollatedNumber(
    @PrimaryKey val id: Long?,
    @CollateNoCase val score: Score,
)

/**
 * The value of a column as it is stored, as text, read without its serializer.
 */
@Serializable
data class StoredText(val value: String?)

/**
 * The value of a column as it is stored, as a number, read without its serializer.
 */
@Serializable
data class StoredNumber(val value: Long?)

/**
 * The table of [ConvertedEvent]s before a migration that adds its column [ConvertedEventV2.reminder].
 */
@Serializable
@DBRow("converted_migration")
data class ConvertedEventV1(@PrimaryKey val id: Uuid, val score: Score)

/**
 * The table of [ConvertedEventV1] after the migration, with a converted column added by `ALTER_ADD_COLUMN`.
 */
@Serializable
@DBRow("converted_migration")
data class ConvertedEventV2(
    @PrimaryKey val id: Uuid,
    val score: Score,
    @Serializable(with = EpochMillisSerializer::class) val reminder: Instant?,
)
