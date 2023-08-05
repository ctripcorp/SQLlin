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

package com.ctrip.sqllin.processor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import java.io.OutputStreamWriter

/**
 * Generate the clause property for data class that present the database entity
 * @author yaqiao
 */

class ClauseProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private companion object {
        const val ANNOTATION_DATABASE_ROW_NAME = "com.ctrip.sqllin.dsl.annotation.DBRow"
        const val ANNOTATION_SERIALIZABLE = "kotlinx.serialization.Serializable"
    }

    private var invoked = false

    @Suppress("UNCHECKED_CAST")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        val allClassAnnotatedWhereProperties = resolver.getSymbolsWithAnnotation(ANNOTATION_DATABASE_ROW_NAME) as Sequence<KSClassDeclaration>
        val serializableType = resolver.getClassDeclarationByName(resolver.getKSNameFromString(ANNOTATION_SERIALIZABLE))!!.asStarProjectedType()

        for (classDeclaration in allClassAnnotatedWhereProperties) {

            if (classDeclaration.annotations.all { !it.annotationType.resolve().isAssignableFrom(serializableType) })
                continue // Don't handle the class that don't annotated 'Serializable'

            val className = classDeclaration.simpleName.asString()
            val packageName = classDeclaration.packageName.asString()
            val objectName = "${className}Table"
            val tableName = classDeclaration.annotations.find {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_DATABASE_ROW_NAME
            }?.arguments?.first()?.value?.takeIf { (it as? String)?.isNotBlank() == true } ?: className

            val outputStream = environment.codeGenerator.createNewFile(
                dependencies = Dependencies(true, classDeclaration.containingFile!!),
                packageName = packageName,
                fileName = objectName,
            )

            OutputStreamWriter(outputStream).use { writer ->
                writer.write("package $packageName\n\n")

                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseBoolean\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseNumber\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseString\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.SetClause\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.Table\n")
                writer.write("import kotlinx.serialization.ExperimentalSerializationApi\n\n")

                writer.write("@OptIn(ExperimentalSerializationApi::class)\n")
                writer.write("object $objectName : Table<$className>(\"$tableName\") {\n\n")

                writer.write("    override fun kSerializer() = $className.serializer()\n\n")

                writer.write("    inline operator fun <R> invoke(block: $objectName.(table: $objectName) -> R): R = this.block(this)\n\n")
                classDeclaration.getAllProperties().forEachIndexed { index, property ->
                    val clauseElementTypeName = getClauseElementTypeStr(property) ?: return@forEachIndexed
                    val propertyName = property.simpleName.asString()
                    val elementName = "$className.serializer().descriptor.getElementName($index)"

                    // Write 'SelectClause' code.
                    writer.write("    val $propertyName\n")
                    writer.write("        get() = $clauseElementTypeName($elementName, this, false)\n\n")

                    // Write 'SetClause' code.
                    val isNotNull = property.type.resolve().nullability == Nullability.NOT_NULL
                    writer.write("    var SetClause<$className>.$propertyName: ${property.typeName}")
                    val nullableSymbol = if (isNotNull) "\n" else "?\n"
                    writer.write(nullableSymbol)
                    writer.write("        get() = ${getSetClauseGetterValue(property)}\n")
                    writer.write("        set(value) = append($elementName, \"${getValueStr(property)}\")\n\n")
                }
                writer.write("}")
            }
        }
        return emptyList()
    }

    private fun getClauseElementTypeStr(property: KSPropertyDeclaration): String? = when (
        property.typeName
    ) {
        Int::class.qualifiedName,
        Long::class.qualifiedName,
        Short::class.qualifiedName,
        Byte::class.qualifiedName,
        Float::class.qualifiedName,
        Double::class.qualifiedName,
        UInt::class.qualifiedName,
        ULong::class.qualifiedName,
        UShort::class.qualifiedName,
        UByte::class.qualifiedName, -> "ClauseNumber"

        Char::class.qualifiedName,
        String::class.qualifiedName, -> "ClauseString"

        Boolean::class.qualifiedName -> "ClauseBoolean"

        else -> null
    }

    private fun getSetClauseGetterValue(property: KSPropertyDeclaration): String? = when (
        property.typeName
    ) {
        Int::class.qualifiedName -> "0"
        Long::class.qualifiedName -> "0l"
        Short::class.qualifiedName -> "0s"
        Byte::class.qualifiedName -> "0b"
        Float::class.qualifiedName -> "0f"
        Double::class.qualifiedName -> "0.0"
        UInt::class.qualifiedName -> "0u"
        ULong::class.qualifiedName -> "0ul"
        UShort::class.qualifiedName -> "0us"
        UByte::class.qualifiedName -> "0ub"
        Boolean::class.qualifiedName -> "false"

        Char::class.qualifiedName -> "'0'"
        String::class.qualifiedName -> "\"\""

        else -> null
    }

    private fun getValueStr(property: KSPropertyDeclaration): String = when (
        property.typeName
    ) {
        Char::class.qualifiedName,
        String::class.qualifiedName -> "'\$value'"
        Boolean::class.qualifiedName -> "\${if (value) 1 else 0}"
        else -> "\$value"
    }

    private inline val KSPropertyDeclaration.typeName
        get() = type.resolve().declaration.qualifiedName?.asString()
}