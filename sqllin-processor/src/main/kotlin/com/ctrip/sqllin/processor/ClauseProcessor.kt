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
import java.lang.IllegalStateException

/**
 * KSP symbol processor that generates table objects for database entities.
 *
 * For each data class annotated with [@DBRow][com.ctrip.sqllin.dsl.annotation.DBRow]
 * and [@Serializable][kotlinx.serialization.Serializable], this processor generates
 * a companion `Table` object (named `{ClassName}Table`) with:
 *
 * ### Generated Features
 * - **Type-safe column property accessors** for SELECT clauses
 * - **Mutable properties** for UPDATE SET clauses
 * - **Compile-time CREATE TABLE statement** with proper SQLite type mappings
 * - **Primary key metadata** extraction from [@PrimaryKey][com.ctrip.sqllin.dsl.annotation.PrimaryKey]
 *   and [@CompositePrimaryKey][com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey] annotations
 * - **Column modifiers** support:
 *   - PRIMARY KEY with optional AUTOINCREMENT
 *   - NOT NULL constraints
 *   - UNIQUE constraints (single and composite)
 *   - COLLATE NOCASE for case-insensitive text columns
 * - **Type support**:
 *   - All Kotlin primitive types and unsigned variants
 *   - String, Char, Boolean, ByteArray
 *   - Enum classes (stored as integers)
 *   - Typealiases of supported types
 *
 * ### Performance Optimization
 * CREATE TABLE statements are generated at **compile-time** rather than runtime,
 * eliminating the overhead of runtime reflection and string building during table creation.
 *
 * @author Yuang Qiao
 * @see com.ctrip.sqllin.dsl.annotation.DBRow
 * @see com.ctrip.sqllin.dsl.annotation.PrimaryKey
 * @see com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey
 * @see com.ctrip.sqllin.dsl.annotation.Unique
 * @see com.ctrip.sqllin.dsl.annotation.CompositeUnique
 * @see com.ctrip.sqllin.dsl.annotation.CollateNoCase
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
        const val ANNOTATION_UNIQUE = "com.ctrip.sqllin.dsl.annotation.Unique"
        const val ANNOTATION_COMPOSITE_UNIQUE = "com.ctrip.sqllin.dsl.annotation.CompositeUnique"
        const val ANNOTATION_NO_CASE = "com.ctrip.sqllin.dsl.annotation.CollateNoCase"
        const val ANNOTATION_SERIALIZABLE = "kotlinx.serialization.Serializable"
        const val ANNOTATION_TRANSIENT = "kotlinx.serialization.Transient"

        const val PROMPT_CANT_ADD_BOTH_ANNOTATION = "You can't add both @PrimaryKey and @CompositePrimaryKey to the same property."
        const val PROMPT_PRIMARY_KEY_MUST_NOT_NULL = "The primary key must be not-null."
        const val PROMPT_PRIMARY_KEY_TYPE = """The primary key's type must be Long when you set the the parameter "isAutoincrement = true" in annotation PrimaryKey."""
        const val PROMPT_PRIMARY_KEY_USE_COUNT = "You only could use PrimaryKey to annotate one property in a class."
        const val PROMPT_NO_CASE_MUST_FOR_TEXT = "You only could add annotation @CollateNoCase for a String or Char typed property."
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

            val foreignKeyParser = ForeignKeyParser()
            foreignKeyParser.handleGroups(classDeclaration.annotations)

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
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseEnum\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseNumber\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseString\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.SetClause\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.PrimaryKeyInfo\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.Table\n\n")

                writer.write("object $objectName : Table<$className>(\"$tableName\") {\n\n")

                writer.write("    override fun kSerializer() = $className.serializer()\n\n")

                writer.write("    inline operator fun <R> invoke(block: $objectName.(table: $objectName) -> R): R = this.block(this)\n\n")
                val transientName = resolver.getClassDeclarationByName(ANNOTATION_TRANSIENT)!!.asStarProjectedType()
                val primaryKeyAnnotationName = resolver.getClassDeclarationByName(ANNOTATION_PRIMARY_KEY)!!.asStarProjectedType()
                val compositePrimaryKeyName = resolver.getClassDeclarationByName(ANNOTATION_COMPOSITE_PRIMARY_KEY)!!.asStarProjectedType()
                val noCaseAnnotationName = resolver.getClassDeclarationByName(ANNOTATION_NO_CASE)!!.asStarProjectedType()
                val uniqueAnnotationName = resolver.getClassDeclarationByName(ANNOTATION_UNIQUE)!!.asStarProjectedType()

                // Primary key tracking for metadata generation
                var primaryKeyName: String? = null
                var isAutomaticIncrement = false
                var isRowId = false
                val compositePrimaryKeys = ArrayList<String>()
                var isContainsPrimaryKey = false

                // CREATE TABLE statement builder (compile-time generation)
                val createSQLBuilder = StringBuilder("CREATE TABLE ").apply {
                    append(tableName)
                    append('(')
                }

                // Track composite unique constraints: group number → list of column names
                val compositeUniqueColumns = HashMap<Int, MutableList<String>>()

                // Filter out @Transient properties and convert to list for indexed iteration
                val propertyList = classDeclaration.getAllProperties().filter { classDeclaration ->
                    !classDeclaration.annotations.any { ksAnnotation -> ksAnnotation.annotationType.resolve().isAssignableFrom(transientName) }
                }.toList()

                // Process each property to generate column definitions
                propertyList.forEachIndexed { index, property ->
                    val clauseElementTypeName = getClauseElementTypeStr(property) ?: return@forEachIndexed
                    val propertyName = property.simpleName.asString()
                    val elementName = "$className.serializer().descriptor.getElementName($index)"
                    val isNotNull = property.type.resolve().nullability == Nullability.NOT_NULL

                    // Collect the information of the primary key(s).
                    val annotationKSType = property.annotations.map { it.annotationType.resolve() }
                    val isPrimaryKey = annotationKSType.any { it.isAssignableFrom(primaryKeyAnnotationName) }

                    // Build column definition: name, type, and constraints
                    with(createSQLBuilder) {
                        append(propertyName)
                        val type = getSQLiteType(property, isPrimaryKey)
                        append(type)

                        // Handle @PrimaryKey annotation
                        if (isPrimaryKey) {
                            check(!annotationKSType.any { it.isAssignableFrom(compositePrimaryKeyName) }) { PROMPT_CANT_ADD_BOTH_ANNOTATION }
                            check(!isNotNull) { PROMPT_PRIMARY_KEY_MUST_NOT_NULL }
                            check(!isContainsPrimaryKey) { PROMPT_PRIMARY_KEY_USE_COUNT }
                            isContainsPrimaryKey = true
                            primaryKeyName = propertyName

                            append(" PRIMARY KEY")

                            isAutomaticIncrement = property.annotations.find {
                                it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_PRIMARY_KEY
                            }?.arguments?.firstOrNull()?.value as? Boolean ?: false
                            val isLong = type == " INTEGER" || type == " BIGINT"
                            if (isAutomaticIncrement) {
                                check(isLong) { PROMPT_PRIMARY_KEY_TYPE }
                                append(" AUTOINCREMENT")
                            }
                            isRowId = isLong
                        } else if (annotationKSType.any { it.isAssignableFrom(compositePrimaryKeyName) }) {
                            // Handle @CompositePrimaryKey - collect for table-level constraint
                            check(isNotNull) { PROMPT_PRIMARY_KEY_MUST_NOT_NULL }
                            compositePrimaryKeys.add(propertyName)
                        } else if (isNotNull) {
                            // Add NOT NULL constraint for non-nullable, non-PK columns
                            append(" NOT NULL")
                        }

                        // Handle @CollateNoCase annotation - must be on text columns
                        if (annotationKSType.any { it.isAssignableFrom(noCaseAnnotationName) }) {
                            check(type == " TEXT" || type == " CHAR(1)") { PROMPT_NO_CASE_MUST_FOR_TEXT }
                            append(" COLLATE NOCASE")
                        }

                        // Handle @Unique annotation - single column uniqueness
                        if (annotationKSType.any { it.isAssignableFrom(uniqueAnnotationName) })
                            append(" UNIQUE")

                        // Handle @Reference and @ForeignKey
                        foreignKeyParser.handleColumnAnnotations(createSQLBuilder, property.annotations, propertyName, isNotNull)

                        // Handle @CompositeUnique annotation - collect for table-level constraint
                        val compositeUniqueAnnotation = property.annotations
                            .find { it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_COMPOSITE_UNIQUE }

                        compositeUniqueAnnotation?.run {
                            // Extract group numbers from annotation (defaults to group 0 if not specified)
                            arguments
                                .firstOrNull { it.name?.asString() == "group" }
                                .let {
                                    val list = if (it == null) {
                                        listOf(0)  // Default to group 0
                                    } else {
                                        it.value as? List<Int> ?: listOf(0)
                                    }
                                    // Add this property to each specified group
                                    list.forEach { group ->
                                        val groupList = compositeUniqueColumns[group] ?: ArrayList<String>().also { gl ->
                                            compositeUniqueColumns[group] = gl
                                        }
                                        groupList.add(propertyName)
                                    }
                                }
                        }

                        if (index < propertyList.lastIndex)
                            append(',')
                    }

                    // Write 'SelectClause' code.
                    writer.write("    @ColumnNameDslMaker\n")
                    writer.write("    val $propertyName\n")
                    writer.write("        get() = $clauseElementTypeName($elementName, this)\n\n")
                    writer.write("    @ColumnNameDslMaker\n")
                    writer.write("    var SetClause<$className>.$propertyName: ${property.typeName}")
                    val nullableSymbol = when {
                        isRowId -> "?\n"
                        isNotNull -> "\n"
                        else -> "?\n"
                    }
                    writer.write(nullableSymbol)
                    writer.write("        get() = ${getSetClauseGetterValue(property)}\n")
                    writer.write("        set(value) = ${appendFunction(elementName, property)}\n\n")
                }

                // Write the override instance for property `primaryKeyInfo`.
                if (primaryKeyName == null && compositePrimaryKeys.isEmpty()) {
                    writer.write("    override val primaryKeyInfo = null\n\n")
                } else {
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
                }

                // Append table-level constraints to CREATE TABLE statement
                with(createSQLBuilder) {
                    // Add composite primary key constraint if present
                    compositePrimaryKeys.takeIf { it.isNotEmpty() }?.let {
                        append(",PRIMARY KEY(")
                        append(it[0])
                        for (i in 1 ..< it.size) {
                            append(',')
                            append(it[i])
                        }
                        append(')')
                    }

                    // Add composite unique constraints for each group
                    compositeUniqueColumns.values.forEach {
                        if (it.isEmpty())
                            return@forEach
                        append(",UNIQUE(")
                        append(it[0])
                        for (i in 1 ..< it.size) {
                            append(',')
                            append(it[i])
                        }
                        append(')')
                    }

                    foreignKeyParser.generateCodeForForeignKey(createSQLBuilder)

                    append(')')
                }

                writer.write("    override val createSQL = \"$createSQLBuilder\"\n")

                writer.write("}\n")
            }
        }
        return invalidateDBRowClasses
    }

    /**
     * Maps a property's Kotlin type to the corresponding clause element type name.
     *
     * Handles three categories:
     * - **Typealiases**: Resolves to underlying type and maps to appropriate clause type
     * - **Enum classes**: Maps to `ClauseEnum<EnumType>` for type-safe enum operations
     * - **Standard types**: Maps to ClauseNumber, ClauseString, ClauseBoolean, or ClauseBlob
     *
     * @param property The property declaration to analyze
     * @return The clause type name (ClauseNumber, ClauseString, ClauseBoolean, ClauseBlob, ClauseEnum), or null if unsupported
     */
    private fun getClauseElementTypeStr(property: KSPropertyDeclaration): String? = when (
        val declaration = property.type.resolve().declaration
    ) {
        is KSTypeAlias -> {
            val realDeclaration = declaration.type.resolve().declaration
            getClauseElementTypeStrByTypeName(realDeclaration.typeName) ?: kotlin.run {
                if (realDeclaration is KSClassDeclaration && realDeclaration.classKind == ClassKind.ENUM_CLASS)
                    "ClauseEnum<${realDeclaration.typeName}>"
                else
                    null
            }
        }
        is KSClassDeclaration if declaration.classKind == ClassKind.ENUM_CLASS -> "ClauseEnum<${declaration.typeName}>"
        else -> getClauseElementTypeStrByTypeName(declaration.typeName)
    }

    /**
     * Maps a fully qualified type name to its corresponding clause element type.
     *
     * Supports primitive types and their unsigned variants:
     * - Numeric types (Byte, Short, Int, Long, Float, Double, UByte, UShort, UInt, ULong) → ClauseNumber
     * - Text types (Char, String) → ClauseString
     * - Boolean → ClauseBoolean
     * - ByteArray → ClauseBlob
     *
     * Note: Enum types are handled separately by [getClauseElementTypeStr].
     *
     * @param typeName The fully qualified type name to map
     * @return The clause type name (ClauseNumber, ClauseString, ClauseBoolean, ClauseBlob), or null if unsupported
     */
    private fun getClauseElementTypeStrByTypeName(typeName: String?): String? = when (typeName) {
        FullNameCache.INT,
        FullNameCache.LONG,
        FullNameCache.SHORT,
        FullNameCache.BYTE,
        FullNameCache.FLOAT,
        FullNameCache.DOUBLE,
        FullNameCache.UINT,
        FullNameCache.ULONG,
        FullNameCache.USHORT,
        FullNameCache.UBYTE, -> "ClauseNumber"

        FullNameCache.CHAR,
        FullNameCache.STRING, -> "ClauseString"

        FullNameCache.BOOLEAN -> "ClauseBoolean"

        FullNameCache.BYTE_ARRAY -> "ClauseBlob"

        else -> null
    }

    /**
     * Generates the default getter value for SetClause properties based on type.
     * Supports typealiases by resolving them to their underlying types.
     *
     * @return The default value string for the property type, or null if unsupported
     */
    private fun getSetClauseGetterValue(property: KSPropertyDeclaration): String? {
        fun KSClassDeclaration.firstEnum() = declarations
            .filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.classKind == ClassKind.ENUM_ENTRY }
            ?.qualifiedName?.asString()
        return when (val declaration = property.type.resolve().declaration) {
            is KSTypeAlias -> {
                val realDeclaration = declaration.type.resolve().declaration
                getDefaultValueByType(realDeclaration.typeName) ?: kotlin.run {
                    if (realDeclaration is KSClassDeclaration && realDeclaration.classKind == ClassKind.ENUM_CLASS)
                        realDeclaration.firstEnum()
                    else
                        null
                }
            }
            is KSClassDeclaration if declaration.classKind == ClassKind.ENUM_CLASS -> declaration.firstEnum()
            else -> getDefaultValueByType(declaration.typeName)
        }
    }

    /**
     * Returns the default value string for a given type name.
     *
     * @param typeName The fully qualified type name
     * @return The default value string (e.g., "0" for Int, "false" for Boolean), or null if unsupported
     */
    private fun getDefaultValueByType(typeName: String?): String? = when (typeName) {
        FullNameCache.INT -> "0"
        FullNameCache.LONG -> "0L"
        FullNameCache.SHORT -> "0"
        FullNameCache.BYTE -> "0"
        FullNameCache.FLOAT -> "0F"
        FullNameCache.DOUBLE -> "0.0"
        FullNameCache.UINT -> "0U"
        FullNameCache.ULONG -> "0UL"
        FullNameCache.USHORT -> "0U"
        FullNameCache.UBYTE -> "0U"
        FullNameCache.BOOLEAN -> "false"

        FullNameCache.CHAR -> "'0'"
        FullNameCache.STRING -> "\"\""

        FullNameCache.BYTE_ARRAY -> "ByteArray(0)"

        else -> null
    }

    /**
     * Generates the appropriate append function call for SetClause setters.
     * Supports typealiases by resolving them to their underlying types.
     *
     * For enum types, converts the enum value to its ordinal before appending.
     * Handles nullable enums with safe-call operator.
     *
     * @param elementName The serialized element name
     * @param property The property declaration
     * @return The append function call string, or null if unsupported type
     */
    private fun appendFunction(elementName: String, property: KSPropertyDeclaration): String? = when (
        val declaration = property.type.resolve().declaration
    ) {
        is KSTypeAlias -> {
            val realDeclaration = declaration.type.resolve().declaration
            appendFunctionByTypeName(elementName, realDeclaration.typeName) ?: kotlin.run {
                if (realDeclaration is KSClassDeclaration && realDeclaration.classKind == ClassKind.ENUM_CLASS)
                    "appendAny($elementName, value?.ordinal)"
                else
                    null
            }
        }
        is KSClassDeclaration if declaration.classKind == ClassKind.ENUM_CLASS -> "appendAny($elementName, value?.ordinal)"
        else -> appendFunctionByTypeName(elementName, declaration.typeName)
    }

    /**
     * Generates the append function call for a given type name.
     *
     * @param elementName The serialized element name
     * @param typeName The fully qualified type name
     * @return The append function call string, or null if unsupported type
     */
    private fun appendFunctionByTypeName(elementName: String, typeName: String?): String? = when (typeName) {
        FullNameCache.INT,
        FullNameCache.LONG,
        FullNameCache.SHORT,
        FullNameCache.BYTE,
        FullNameCache.FLOAT,
        FullNameCache.DOUBLE,
        FullNameCache.UINT,
        FullNameCache.ULONG,
        FullNameCache.USHORT,
        FullNameCache.UBYTE,
        FullNameCache.CHAR,
        FullNameCache.STRING,
        FullNameCache.BOOLEAN,
        FullNameCache.BYTE_ARRAY -> "appendAny($elementName, value)"
        else -> null
    }

    /**
     * Determines the SQLite type declaration for a given property.
     *
     * This function resolves the Kotlin type of a property to its corresponding SQLite type
     * string, handling type aliases and enum classes. The result is used in compile-time
     * CREATE TABLE statement generation.
     *
     * ### Type Resolution Strategy
     * 1. **Type Aliases**: Resolves to the underlying type, then maps to SQLite type
     * 2. **Enum Classes**: Maps to SQLite INT type (enums are stored as ordinals)
     * 3. **Standard Types**: Direct mapping via [FullNameCache.getSQLTypeName]
     *
     * ### Primary Key Special Handling
     * When `isPrimaryKey` is true and the property is of type [Long], the function returns
     * " INTEGER" instead of " BIGINT" to enable SQLite's rowid aliasing optimization.
     *
     * ### Example Mappings
     * ```kotlin
     * // Standard type
     * val age: Int  // → " INT"
     *
     * // Type alias
     * typealias UserId = Long
     * val id: UserId  // → " BIGINT" (or " INTEGER" if primary key)
     *
     * // Enum class
     * enum class Status { ACTIVE, INACTIVE }
     * val status: Status  // → " INT"
     * ```
     *
     * @param property The KSP property declaration to analyze
     * @param isPrimaryKey Whether this property is annotated with [@PrimaryKey]
     * @return SQLite type declaration string with leading space (e.g., " INT", " TEXT")
     * @throws IllegalStateException if the property type is not supported by SQLlin
     *
     * @see FullNameCache.getSQLTypeName
     */
    private fun getSQLiteType(property: KSPropertyDeclaration, isPrimaryKey: Boolean): String {
        val declaration = property.type.resolve().declaration
        return when (declaration) {
            is KSTypeAlias -> {
                val realDeclaration = declaration.type.resolve().declaration
                FullNameCache.getSQLTypeName(realDeclaration.typeName, isPrimaryKey) ?: kotlin.run {
                    if (realDeclaration is KSClassDeclaration && realDeclaration.classKind == ClassKind.ENUM_CLASS)
                        FullNameCache.getSQLTypeName(FullNameCache.INT, isPrimaryKey)
                    else
                        null
                }
            }
            is KSClassDeclaration if declaration.classKind == ClassKind.ENUM_CLASS ->
                FullNameCache.getSQLTypeName(FullNameCache.INT, isPrimaryKey)
            else -> FullNameCache.getSQLTypeName(declaration.typeName, isPrimaryKey)
        } ?: throw IllegalStateException("Hasn't support the type '${declaration.typeName}' yet")
    }

    /**
     * Extension property that resolves a property's fully qualified type name.
     */
    private inline val KSPropertyDeclaration.typeName
        get() = type.resolve().declaration.qualifiedName?.asString()

    /**
     * Extension property that retrieves a declaration's fully qualified type name.
     */
    private inline val KSDeclaration.typeName
        get() = qualifiedName?.asString()
}