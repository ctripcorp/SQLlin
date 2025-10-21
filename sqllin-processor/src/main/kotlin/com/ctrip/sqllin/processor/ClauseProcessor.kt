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
 * KSP symbol processor that generates table objects for database entities.
 *
 * For each data class annotated with [@DBRow][com.ctrip.sqllin.dsl.annotation.DBRow]
 * and [@Serializable][kotlinx.serialization.Serializable], this processor generates
 * a companion `Table` object (named `{ClassName}Table`) with:
 *
 * - Type-safe column property accessors for SELECT clauses
 * - Mutable properties for UPDATE SET clauses
 * - Primary key metadata extraction from [@PrimaryKey][com.ctrip.sqllin.dsl.annotation.PrimaryKey]
 *   and [@CompositePrimaryKey][com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey] annotations
 *
 * The generated code provides compile-time safety for SQL DSL operations.
 *
 * @author Yuang Qiao
 */
class ClauseProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    /**
     * Annotation names and validation messages used during processing.
     */
    private companion object {
        const val ANNOTATION_DATABASE_ROW_NAME = "com.ctrip.sqllin.dsl.annotation.DBRow"
        const val ANNOTATION_PRIMARY_KEY = "com.ctrip.sqllin.dsl.annotation.PrimaryKey"
        const val ANNOTATION_COMPOSITE_PRIMARY_KEY = "com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey"
        const val ANNOTATION_SERIALIZABLE = "kotlinx.serialization.Serializable"
        const val ANNOTATION_TRANSIENT = "kotlinx.serialization.Transient"

        const val PROMPT_CANT_ADD_BOTH_ANNOTATION = "You can't add both @PrimaryKey and @CompositePrimaryKey to the same property."
        const val PROMPT_PRIMARY_KEY_MUST_NOT_NULL = "The primary key must be not-null."
        const val PROMPT_PRIMARY_KEY_TYPE = """The primary key's type must be Long when you set the the parameter "isAutoincrement = true" in annotation PrimaryKey."""
        const val PROMPT_PRIMARY_KEY_USE_COUNT = "You only could use PrimaryKey to annotate one property in a class."
    }

    /**
     * Processes all [@DBRow][com.ctrip.sqllin.dsl.annotation.DBRow] annotated classes
     * and generates corresponding table objects.
     *
     * @return List of symbols that couldn't be processed (due to validation failures)
     */
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
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseBlob\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseBoolean\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseNumber\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseString\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.DefaultClauseBlob\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.SetClause\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.PrimaryKeyInfo\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.Table\n\n")

                writer.write("object $objectName : Table<$className>(\"$tableName\") {\n\n")

                writer.write("    override fun kSerializer() = $className.serializer()\n\n")

                writer.write("    inline operator fun <R> invoke(block: $objectName.(table: $objectName) -> R): R = this.block(this)\n\n")
                val transientName = resolver.getClassDeclarationByName(ANNOTATION_TRANSIENT)!!.asStarProjectedType()
                val primaryKeyAnnotationName = resolver.getClassDeclarationByName(ANNOTATION_PRIMARY_KEY)!!.asStarProjectedType()
                val compositePrimaryKeyName = resolver.getClassDeclarationByName(ANNOTATION_COMPOSITE_PRIMARY_KEY)!!.asStarProjectedType()

                var primaryKeyName: String? = null
                var isAutomaticIncrement = false
                var isRowId = false
                val compositePrimaryKeys = ArrayList<String>()
                var isContainsPrimaryKey = false

                classDeclaration.getAllProperties().filter { classDeclaration ->
                    !classDeclaration.annotations.any { ksAnnotation -> ksAnnotation.annotationType.resolve().isAssignableFrom(transientName) }
                }.forEachIndexed { index, property ->
                    val clauseElementTypeName = getClauseElementTypeStr(property) ?: return@forEachIndexed
                    val propertyName = property.simpleName.asString()
                    val elementName = "$className.serializer().descriptor.getElementName($index)"
                    val isNotNull = property.type.resolve().nullability == Nullability.NOT_NULL

                    // Collect the information of the primary key(s).
                    val annotations = property.annotations.map { it.annotationType.resolve() }
                    val isPrimaryKey = annotations.any { it.isAssignableFrom(primaryKeyAnnotationName) }
                    val isLong = property.typeName == Long::class.qualifiedName
                    if (isPrimaryKey) {
                        check(!annotations.any { it.isAssignableFrom(compositePrimaryKeyName) }) { PROMPT_CANT_ADD_BOTH_ANNOTATION }
                        check(!isNotNull) { PROMPT_PRIMARY_KEY_MUST_NOT_NULL }
                        check(!isContainsPrimaryKey) { PROMPT_PRIMARY_KEY_USE_COUNT }
                        isContainsPrimaryKey = true
                        primaryKeyName = propertyName
                        isAutomaticIncrement = property.annotations.find {
                            it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_PRIMARY_KEY
                        }?.arguments?.firstOrNull()?.value as? Boolean ?: false
                        if (isAutomaticIncrement)
                            check(isLong) { PROMPT_PRIMARY_KEY_TYPE }
                        isRowId = isLong
                    } else if (annotations.any { it.isAssignableFrom(compositePrimaryKeyName) }) {
                        check(isNotNull) { PROMPT_PRIMARY_KEY_MUST_NOT_NULL }
                        compositePrimaryKeys.add(propertyName)
                    }

                    // Write 'SelectClause' code.
                    writer.write("    @ColumnNameDslMaker\n")
                    writer.write("    val $propertyName\n")
                    writer.write("        get() = $clauseElementTypeName($elementName, this, false)\n\n")

                    // Write 'SetClause' code.
                    writer.write("    @ColumnNameDslMaker\n")
                    writer.write("    var SetClause<$className>.$propertyName: ${property.typeName}")
                    val nullableSymbol = when {
                        isRowId -> "?\n"
                        isNotNull -> "\n"
                        else -> "?\n"
                    }
                    writer.write(nullableSymbol)
                    writer.write("        get() = ${getSetClauseGetterValue(property)}\n")
                    writer.write("        set(value) = ${appendFunction(elementName, property, isNotNull)}\n\n")
                }

                // Write the override instance for property `primaryKeyInfo`.
                if (primaryKeyName == null && compositePrimaryKeys.isEmpty()) {
                    writer.write("    override val primaryKeyInfo = null\n\n")
                    writer.write("}\n")
                    return@use
                }
                writer.write("    override val primaryKeyInfo = PrimaryKeyInfo(\n")
                if (primaryKeyName == null) {
                    writer.write("        primaryKeyName = null,\n")
                } else {
                    writer.write("        primaryKeyName = \"$primaryKeyName\",\n")
                }
                writer.write("        isAutomaticIncrement = $isAutomaticIncrement,\n")
                writer.write("        isRowId = $isRowId,\n")
                if (compositePrimaryKeys.isEmpty()) {
                    writer.write("        compositePrimaryKeys = null,\n")
                } else {
                    writer.write("        compositePrimaryKeys = listOf(\n")
                    compositePrimaryKeys.forEach {
                        writer.write("            \"$it\",\n")
                    }
                    writer.write("        )\n")
                }
                writer.write("    )\n\n")
                writer.write("}\n")
            }
        }
        return invalidateDBRowClasses
    }

    /**
     * Maps a property's Kotlin type to the corresponding clause element type name.
     *
     * @return The clause type name (ClauseNumber, ClauseString, ClauseBoolean, ClauseBlob), or null if unsupported
     */
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

        ByteArray::class.qualifiedName -> "ClauseBlob"

        else -> null
    }

    /**
     * Generates the default getter value for SetClause properties based on type.
     *
     * @return The default value string for the property type, or null if unsupported
     */
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

        ByteArray::class.qualifiedName -> "ByteArray(0)"

        else -> null
    }

    /**
     * Generates the appropriate append function call for SetClause setters.
     *
     * @param elementName The serialized element name
     * @param property The property declaration
     * @param isNotNull Whether the property is non-nullable
     * @return The append function call string, or null if unsupported type
     */
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
        UByte::class.qualifiedName,
        Char::class.qualifiedName,
        String::class.qualifiedName,
        Boolean::class.qualifiedName,
        ByteArray::class.qualifiedName -> "appendAny($elementName, value)"
        else -> null
    }

    /**
     * Extension property that resolves a property's fully qualified type name.
     */
    private inline val KSPropertyDeclaration.typeName
        get() = type.resolve().declaration.qualifiedName?.asString()
}