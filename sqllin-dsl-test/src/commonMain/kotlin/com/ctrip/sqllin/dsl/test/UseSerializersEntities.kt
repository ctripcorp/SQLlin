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

@file:UseSerializers(DurationAsSecondsSerializer::class)

package com.ctrip.sqllin.dsl.test

import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * An entity whose file gives the serializer of a type, which CommonConvertedTest tests.
 */

/**
 * Stores a duration as its whole seconds, as the serializer of the durations of this file.
 */
object DurationAsSecondsSerializer : KSerializer<Duration> {
    override val descriptor = PrimitiveSerialDescriptor("com.ctrip.sqllin.dsl.test.DurationAsSeconds", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeLong(value.inWholeSeconds)
    override fun deserialize(decoder: Decoder): Duration = decoder.decodeLong().seconds
}

/**
 * Durations stored as numbers of seconds, by the serializer that `@file:UseSerializers` gives, not as the ISO-8601
 * strings of their built-in serializer.
 */
@Serializable
@DBRow("converted_timer")
data class ConvertedTimer(
    @PrimaryKey val id: Long?,
    val timeout: Duration,
    val delay: Duration?,
)
