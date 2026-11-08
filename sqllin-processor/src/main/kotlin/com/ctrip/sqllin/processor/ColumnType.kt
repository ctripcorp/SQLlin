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

package com.ctrip.sqllin.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.Visibility

/**
 * What the column of a property of a `@DBRow` or `@DBView` class holds, as the serializer that kotlinx.serialization
 * writes the property with stores it.
 *
 * SQLlin maps a property of a basic type, or of an enum class, with its default serializer, to a column of its own,
 * whose type is known when the table is generated. Any other serializer converts the values: the column holds what it
 * encodes, and the column's element converts the values given to its conditions with it, so that they compare what is
 * stored.
 *
 * @author Yuang Qiao
 */
sealed interface ColumnType {

    /**
     * A property of a basic type with its default serializer: a number, Boolean, Char, String or ByteArray.
     *
     * @property typeName The qualified name of the type, as `kotlin.Int`
     */
    data class Basic(val typeName: String) : ColumnType

    /**
     * A property of an enum class with its default serializer, which stores the ordinal of an entry.
     */
    data class DefaultEnum(val declaration: KSClassDeclaration) : ColumnType

    /**
     * A property whose serializer converts its values to those of a column.
     *
     * @property typeName The type, with qualified names, as `kotlin.uuid.Uuid` or `kotlin.collections.List<kotlin.String>`
     * @property serializer The Kotlin expression of the serializer, as `com.example.EpochMillisSerializer`
     */
    data class Converted(val typeName: String, val serializer: String) : ColumnType

    /**
     * A property that no column can hold, for [reason], which completes a sentence about the property.
     */
    data class Unsupported(val reason: String) : ColumnType
}

/**
 * Finds the [ColumnType] of a property, through the serializer that the kotlinx.serialization plugin writes it with,
 * which it looks for as the plugin does, first found first:
 * 1. `@Serializable(with = ...)` on the property;
 * 2. `@Contextual` on the property;
 * 3. `@Serializable(with = ...)` on the use of its type, directly or in the type alias it is declared with;
 * 4. a serializer of its type in the `@file:UseSerializers` of the class's file;
 * 5. `@Contextual` on the use of its type, or its type in the `@file:UseContextualSerialization` of the file;
 * 6. the serializer of its type: the default of a basic type or an enum class, or that of a class that declares one with
 *    `@Serializable(with = ...)`, of a value class, or one of the built-in serializers of `Uuid`, `Instant` and
 *    `Duration`.
 *
 * A contextual serializer is the one the encoder's `SerializersModule` gives, and SQLlin's has none, so it is the
 * default serializer of the type, which only a basic type and an enum class without a serializer of its own can have
 * here: any other serializer is converting, and its column's type is computed from it, which a contextual one doesn't
 * describe.
 *
 * @author Yuang Qiao
 */
object ColumnTypes {

    private const val SERIALIZABLE = "kotlinx.serialization.Serializable"
    private const val CONTEXTUAL = "kotlinx.serialization.Contextual"
    private const val USE_SERIALIZERS = "kotlinx.serialization.UseSerializers"
    private const val USE_CONTEXTUAL_SERIALIZATION = "kotlinx.serialization.UseContextualSerialization"
    private const val K_SERIALIZER = "kotlinx.serialization.KSerializer"

    /** The types whose serializers kotlinx.serialization has built in, which encode them as strings. */
    private val BUILT_IN_SERIALIZED = setOf("kotlin.uuid.Uuid", "kotlin.time.Instant", "kotlin.time.Duration")

    private val BASIC_TYPES = with(FullNameCache) {
        setOf(BYTE, SHORT, INT, LONG, UBYTE, USHORT, UINT, ULONG, FLOAT, DOUBLE, BOOLEAN, CHAR, STRING, BYTE_ARRAY)
    }

    private val KSAnnotation.qualifiedName: String?
        get() = annotationType.resolve().declaration.qualifiedName?.asString()

    /**
     * The serializer that `@Serializable(with = ...)` gives, or null for `@Serializable` without one, whose default
     * `with` is `KSerializer` itself, or another annotation.
     */
    private fun KSAnnotation.serializerWith(): KSType? {
        if (qualifiedName != SERIALIZABLE)
            return null
        val with = arguments.firstOrNull { it.name?.asString() == "with" }?.value as? KSType ?: return null
        return with.takeIf { it.declaration.qualifiedName?.asString() != K_SERIALIZER }
    }

    private fun Sequence<KSAnnotation>.serializerWith(): KSType? = firstNotNullOfOrNull { it.serializerWith() }

    private fun Sequence<KSAnnotation>.isContextual(): Boolean = any { it.qualifiedName == CONTEXTUAL }

    /**
     * The classes that the arguments of the annotations [name] of [file] give, as the serializers of
     * `@file:UseSerializers(...)`.
     */
    private fun classesOf(file: KSFile?, name: String): List<KSClassDeclaration> =
        file?.annotations.orEmpty()
            .filter { it.qualifiedName == name }
            .flatMap { annotation -> annotation.arguments.flatMap { (it.value as? List<*>).orEmpty() } }
            .mapNotNull { (it as? KSType)?.declaration as? KSClassDeclaration }
            .toList()

    /**
     * Returns the [ColumnType] of [property], declared in [file].
     */
    fun of(property: KSPropertyDeclaration, file: KSFile?): ColumnType {
        // The type, through its type aliases, and the annotations of its uses, the outermost first
        var type = property.type.resolve()
        val typeAnnotations = ArrayList<KSAnnotation>()
        typeAnnotations += property.type.annotations
        typeAnnotations += type.annotations
        while (true) {
            val alias = type.declaration as? KSTypeAlias ?: break
            typeAnnotations += alias.type.annotations
            type = alias.type.resolve()
            typeAnnotations += type.annotations
        }
        val declaration = type.declaration as? KSClassDeclaration
            ?: return ColumnType.Unsupported("has the type '$type', which is a type parameter, of which no serializer is known")
        val className = declaration.qualifiedName?.asString()
            ?: return ColumnType.Unsupported("has the type '$type', which has no qualified name")
        // The type with its arguments, as `kotlin.collections.List<kotlin.String>`, which the column's element has
        val typeName = type.makeNotNullable().qualifiedString()

        property.annotations.serializerWith()?.let { return converted(typeName, it) }
        var isContextual = property.annotations.isContextual()
        if (!isContextual) {
            typeAnnotations.asSequence().serializerWith()?.let { return converted(typeName, it) }
            classesOf(file, USE_SERIALIZERS).firstOrNull { serializedTypeName(it) == className }?.let {
                return converted(typeName, it.asStarProjectedType())
            }
            isContextual = typeAnnotations.asSequence().isContextual() ||
                classesOf(file, USE_CONTEXTUAL_SERIALIZATION).any { it.qualifiedName?.asString() == className }
        }

        val classSerializer = declaration.annotations.serializerWith()
        return when {
            className in BASIC_TYPES -> ColumnType.Basic(className)
            declaration.classKind == ClassKind.ENUM_CLASS && classSerializer == null -> ColumnType.DefaultEnum(declaration)
            isContextual -> ColumnType.Unsupported(
                "is serialized contextually, with @Contextual or @UseContextualSerialization, while SQLlin has no SerializersModule to find its serializer in. " +
                    "Give it its serializer with @Serializable(with = ...) on the property"
            )
            classSerializer != null
                || Modifier.VALUE in declaration.modifiers || Modifier.INLINE in declaration.modifiers
                || className in BUILT_IN_SERIALIZED -> ColumnType.Converted(typeName, "kotlinx.serialization.serializer<$typeName>()")
            else -> ColumnType.Unsupported(
                "has the type '$type', which no column can hold. A column holds Byte, Short, Int, Long, Float, Double and their unsigned variants, Boolean, Char, String, " +
                    "ByteArray, an enum class, or a type whose serializer encodes it as one of these: Uuid, Instant or Duration, a value class, or a type given a serializer with " +
                    "@Serializable(with = ...) on the property, the use of the type or its class, or by @file:UseSerializers. A collection, or a class serialized as a structure, needs a serializer that " +
                    "encodes it as one value, as JSON in a string. To keep the property out of the table, annotate it with @kotlinx.serialization.Transient"
            )
        }
    }

    /**
     * The column of a property of [typeName] whose serializer is [serializer], created as the plugin creates it: an object
     * as it is, and a class with its constructor without arguments.
     */
    private fun converted(typeName: String, serializer: KSType): ColumnType {
        val declaration = serializer.declaration as? KSClassDeclaration
            ?: return ColumnType.Unsupported("has the serializer '$serializer', which isn't a class")
        val name = declaration.qualifiedName?.asString()
            ?: return ColumnType.Unsupported("has the serializer '$serializer', which has no qualified name")
        val visibility = declaration.getVisibility()
        if (visibility == Visibility.PRIVATE || visibility == Visibility.LOCAL)
            return ColumnType.Unsupported("has the serializer '$name', which is ${visibility.name.lowercase()}, so that the table generated for it can't use it. Make it internal or public")
        val expression = when {
            declaration.classKind == ClassKind.OBJECT -> name
            declaration.getConstructors().none() || declaration.getConstructors().any { constructor -> constructor.parameters.all { it.hasDefault } } -> "$name()"
            else -> return ColumnType.Unsupported("has the serializer '$name', a class without a constructor that takes no arguments, which the table generated for it creates it with")
        }
        return ColumnType.Converted(typeName, expression)
    }

    /**
     * This type as Kotlin code, with qualified names: its class, or type alias, its arguments, and whether it is
     * nullable, as `kotlin.collections.Map<kotlin.String, kotlin.Int?>`.
     */
    private fun KSType.qualifiedString(): String = buildString {
        append(declaration.qualifiedName?.asString() ?: declaration.simpleName.asString())
        if (arguments.isNotEmpty()) {
            append(arguments.joinToString(prefix = "<", postfix = ">") { argument ->
                val argumentType = argument.type?.resolve()
                when {
                    argument.variance == Variance.STAR || argumentType == null -> "*"
                    argument.variance == Variance.COVARIANT -> "out ${argumentType.qualifiedString()}"
                    argument.variance == Variance.CONTRAVARIANT -> "in ${argumentType.qualifiedString()}"
                    else -> argumentType.qualifiedString()
                }
            })
        }
        if (isMarkedNullable)
            append('?')
    }

    /**
     * The qualified name of the type [serializer] serializes, the argument of its `KSerializer`.
     */
    private fun serializedTypeName(serializer: KSClassDeclaration): String? =
        serializer.getAllSuperTypes()
            .firstOrNull { it.declaration.qualifiedName?.asString() == K_SERIALIZER }
            ?.arguments?.firstOrNull()?.type?.resolve()?.declaration?.qualifiedName?.asString()
}
