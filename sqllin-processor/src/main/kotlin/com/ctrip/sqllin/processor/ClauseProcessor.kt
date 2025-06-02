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

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import java.io.OutputStreamWriter

/**
 * Generate the clause properties for data classes that present the database entity
 * @author yaqiao
 */

class ClauseProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private companion object {
        const val ANNOTATION_DATABASE_ROW_NAME = "com.ctrip.sqllin.dsl.annotation.DBRow"
        const val ANNOTATION_SERIALIZABLE = "kotlinx.serialization.Serializable"
        const val ANNOTATION_TRANSIENT = "kotlinx.serialization.Transient"
    }

    @Suppress("UNCHECKED_CAST")
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val allDBRowClasses = resolver.getSymbolsWithAnnotation(ANNOTATION_DATABASE_ROW_NAME)
        val invalidateDBRowClasses = allDBRowClasses.filter { !it.validate() }.toList()

        val validateDBRowClasses = allDBRowClasses.filter { it.validate() } as Sequence<KSClassDeclaration>
        val serializableType = resolver.getClassDeclarationByName(ANNOTATION_SERIALIZABLE)!!.asStarProjectedType()

        for (classDeclaration in validateDBRowClasses) {

            if (classDeclaration.annotations.all { !it.annotationType.resolve().isAssignableFrom(serializableType) })
                continue // Don't handle the classes that didn't be annotated 'Serializable'

            val className = classDeclaration.simpleName.asString()
            val packageName = classDeclaration.packageName.asString()
            val objectName = "${className}Table"
            val tableName = classDeclaration.annotations.find {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_DATABASE_ROW_NAME
            }?.arguments?.firstOrNull()?.value?.takeIf { (it as? String)?.isNotBlank() == true } ?: className

            val outputStream = environment.codeGenerator.createNewFile(
                dependencies = classDeclaration.containingFile?.let { Dependencies(true, it) } ?: Dependencies(true),
                packageName = packageName,
                fileName = objectName,
            )

            OutputStreamWriter(outputStream).use { writer ->
                writer.write("package $packageName\n\n")

                writer.write("import com.ctrip.sqllin.dsl.annotation.ColumnNameDslMaker\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseBoolean\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseNumber\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseString\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.SetClause\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.Table\n\n")

                writer.write("object $objectName : Table<$className>(\"$tableName\") {\n\n")

                writer.write("    override fun kSerializer() = $className.serializer()\n\n")

                writer.write("    inline operator fun <R> invoke(block: $objectName.(table: $objectName) -> R): R = this.block(this)\n\n")
                val transientName = resolver.getClassDeclarationByName(ANNOTATION_TRANSIENT)!!.asStarProjectedType()
                classDeclaration.getAllProperties().filter { classDeclaration ->
                    !classDeclaration.annotations.any { ksAnnotation -> ksAnnotation.annotationType.resolve().isAssignableFrom(transientName) }
                }.forEachIndexed { index, property ->
                    val clauseElementTypeName = getClauseElementTypeStr(property) ?: return@forEachIndexed
                    val propertyName = property.simpleName.asString()
                    val elementName = "$className.serializer().descriptor.getElementName($index)"

                    // Write 'SelectClause' code.
                    writer.write("    @ColumnNameDslMaker\n")
                    writer.write("    val $propertyName\n")
                    writer.write("        get() = $clauseElementTypeName($elementName, this, false)\n\n")

                    // Write 'SetClause' code.
                    writer.write("    @ColumnNameDslMaker\n")
                    val isNotNull = property.type.resolve().nullability == Nullability.NOT_NULL
                    writer.write("    var SetClause<$className>.$propertyName: ${property.typeName}")
                    val nullableSymbol = if (isNotNull) "\n" else "?\n"
                    writer.write(nullableSymbol)
                    writer.write("        get() = ${getSetClauseGetterValue(property)}\n")
                    writer.write("        set(value) = ${appendFunction(elementName, property, isNotNull)}\n\n")
                }
                writer.write("}")
            }
        }
        return invalidateDBRowClasses
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
        Long::class.qualifiedName -> "0L"
        Short::class.qualifiedName -> "0"
        Byte::class.qualifiedName -> "0"
        Float::class.qualifiedName -> "0F"
        Double::class.qualifiedName -> "0.0"
        UInt::class.qualifiedName -> "0U"
        ULong::class.qualifiedName -> "0UL"
        UShort::class.qualifiedName -> "0U"
        UByte::class.qualifiedName -> "0U"
        Boolean::class.qualifiedName -> "false"

        Char::class.qualifiedName -> "'0'"
        String::class.qualifiedName -> "\"\""

        else -> null
    }

    private fun appendFunction(elementName: String, property: KSPropertyDeclaration, isNotNull: Boolean): String? = when (property.typeName) {
        Int::class.qualifiedName,
        Long::class.qualifiedName,
        Short::class.qualifiedName,
        Byte::class.qualifiedName,
        Float::class.qualifiedName,
        Double::class.qualifiedName,
        UInt::class.qualifiedName,
        ULong::class.qualifiedName,
        UShort::class.qualifiedName,
        UByte::class.qualifiedName, -> "appendAny($elementName, value)"

        Char::class.qualifiedName -> "appendString($elementName, value${if (isNotNull) "" else "?"}.toString())"
        String::class.qualifiedName -> "appendString($elementName, value)"

        Boolean::class.qualifiedName -> "appendAny($elementName, value${if (isNotNull) "" else "?"}.let { if (it) 1 else 0 })"
        else -> null
    }

    private inline val KSPropertyDeclaration.typeName
        get() = type.resolve().declaration.qualifiedName?.asString()
}