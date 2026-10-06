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
import com.google.devtools.ksp.getVisibility
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
        const val ANNOTATION_DATABASE_VIEW_NAME = "com.ctrip.sqllin.dsl.annotation.DBView"
        const val ANNOTATION_PACKAGE = "com.ctrip.sqllin.dsl.annotation"

        /** The annotations that declare constraints, which only a table's columns can have. */
        val CONSTRAINT_ANNOTATIONS = listOf(
            "PrimaryKey", "CompositePrimaryKey", "CollateNoCase", "Unique", "CompositeUnique",
            "ForeignKeyGroup", "References", "ForeignKey", "Default",
        ).map { "$ANNOTATION_PACKAGE.$it" }.toSet()
        const val ANNOTATION_SERIALIZABLE = "kotlinx.serialization.Serializable"
        const val ANNOTATION_TRANSIENT = "kotlinx.serialization.Transient"
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
        val invalidateDBRowClasses = allDBRowClasses.filter { !it.validate(enableNewFeatures = false) }.toList()

        val validateDBRowClasses = allDBRowClasses.filter { it.validate(enableNewFeatures = false) } as Sequence<KSClassDeclaration>
        val serializableType = resolver.getClassDeclarationByName(ANNOTATION_SERIALIZABLE)!!.asStarProjectedType()

        for (classDeclaration in validateDBRowClasses) {

            if (classDeclaration.annotations.all { !it.annotationType.resolve().isAssignableFrom(serializableType) })
                continue // Don't handle the classes that didn't be annotated 'Serializable'

            // The generated table object must not be more visible than the entity it is built for,
            // otherwise an 'internal' @DBRow class produces a 'public' object that exposes it.
            val visibility = classDeclaration.getVisibility()
            if (visibility != Visibility.PUBLIC && visibility != Visibility.INTERNAL) {
                environment.logger.error(
                    "The class annotated with '@DBRow' must be 'public' or 'internal', but " +
                        "'${classDeclaration.simpleName.asString()}' is '${visibility.name.lowercase()}'.",
                    classDeclaration,
                )
                continue
            }
            val visibilityModifier = if (visibility == Visibility.INTERNAL) "internal " else ""

            val foreignKeyParser = ForeignKeyParser()
            foreignKeyParser.parseGroups(classDeclaration.annotations)

            val className = classDeclaration.simpleName.asString()
            val packageName = classDeclaration.packageName.asString()
            val objectName = "${className}Table"
            val tableName = classDeclaration.annotations.find {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_DATABASE_ROW_NAME
            }?.arguments?.firstOrNull()?.value?.takeIf { (it as? String)?.isNotBlank() == true } ?: className

            // Keep exactly the properties the serializer writes, in its order, as the generated accessors look a column
            // up by its index in the serializer's descriptor. That leaves out @Transient properties and, because
            // kotlinx.serialization only serializes properties backed by a field, computed ones like `val x get() = ...`
            val transientName = resolver.getClassDeclarationByName(ANNOTATION_TRANSIENT)!!.asStarProjectedType()
            val propertyList = classDeclaration.getAllProperties().filter { property ->
                property.hasBackingField &&
                    !property.annotations.any { ksAnnotation -> ksAnnotation.annotationType.resolve().isAssignableFrom(transientName) }
            }.toList()

            // Every stored property needs a column. A property of a type no column can hold used to be skipped silently:
            // left out of CREATE TABLE while its serializer still wrote and read it, so it only failed at runtime, and as
            // the last property it left a trailing comma that made CREATE TABLE itself invalid. Report all of them here.
            val unsupportedProperties = propertyList.filter { getClauseElementTypeStr(it) == null }
            unsupportedProperties.forEach { property ->
                environment.logger.error(
                    "The property '${property.simpleName.asString()}' of '@DBRow' class '$className' has the type " +
                        "'${property.type.resolve()}', which no column can hold. Supported types are Byte, Short, Int, Long, " +
                        "Float, Double and their unsigned variants, Boolean, Char, String, ByteArray, enum classes, and type " +
                        "aliases of these. To keep the property out of the table, annotate it with @kotlinx.serialization.Transient.",
                    property,
                )
            }
            if (unsupportedProperties.isNotEmpty())
                continue

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

                // The column properties carry @ColumnNameDslMaker for IntelliJ IDEA's DSL highlighting, a target the compiler
                // flags as having no effect on scope control. This code is compiled in the user's module, so keep it quiet.
                writer.write("@Suppress(\"DSL_MARKER_APPLIED_TO_WRONG_TARGET\")\n")
                writer.write("${visibilityModifier}object $objectName : Table<$className>(\"$tableName\") {\n\n")

                writer.write("    override fun kSerializer() = $className.serializer()\n\n")

                writer.write("    inline operator fun <R> invoke(block: $objectName.(table: $objectName) -> R): R = this.block(this)\n\n")

                val columnConstraintParser = ColumnConstraintParser(resolver)

                // CREATE TABLE statement builder (compile-time generation)
                val createSQLBuilder = StringBuilder("CREATE TABLE ").apply {
                    append(tableName)
                    append('(')
                }

                // Process each property to generate column definitions
                propertyList.forEachIndexed { index, property ->
                    val clauseElementTypeName = checkNotNull(getClauseElementTypeStr(property)) // Rejected above
                    val propertyName = property.simpleName.asString()
                    val elementName = "$className.serializer().descriptor.getElementName($index)"
                    val isNotNull = property.type.resolve().nullability == Nullability.NOT_NULL

                    // Build column definition: name, type, and constraints
                    with(createSQLBuilder) {
                        append(propertyName)

                        columnConstraintParser.parseProperty(this, property, propertyName, isNotNull)

                        // Handle @Reference and @ForeignKey
                        foreignKeyParser.parseColumnAnnotations(createSQLBuilder, property.annotations, propertyName, isNotNull)

                        if (index < propertyList.lastIndex)
                            append(',')
                    }

                    // Write 'SelectClause' code.
                    writer.write("    @ColumnNameDslMaker\n")
                    writer.write("    val $propertyName\n")
                    writer.write("        get() = $clauseElementTypeName($elementName, this, ${!isNotNull})\n\n")
                    writer.write("    @ColumnNameDslMaker\n")
                    writer.write("    var SetClause<$className>.$propertyName: ${property.typeName}")
                    writer.write(if (isNotNull) "\n" else "?\n")
                    writer.write("        get() = ${getSetClauseGetterValue(property)}\n")
                    writer.write("        set(value) = ${appendFunction(elementName, property, isNotNull)}\n\n")
                }

                columnConstraintParser.generateCodeForPrimaryKey(writer, createSQLBuilder)
                foreignKeyParser.generateCodeForForeignKey(createSQLBuilder)
                createSQLBuilder.append(')')

                writer.write("    override val createSQL = \"$createSQLBuilder\"\n")

                writer.write("}\n")
            }
        }
        return invalidateDBRowClasses + processViews(resolver)
    }

    /**
     * Processes all [@DBView][com.ctrip.sqllin.dsl.annotation.DBView] annotated classes and generates their view
     * objects: like a table object, with a column property per serialized property, but without SET properties,
     * constraints or a CREATE statement, as a view is created from a SELECT and can't be written to.
     *
     * @return The symbols that couldn't be processed yet
     */
    @Suppress("UNCHECKED_CAST")
    private fun processViews(resolver: Resolver): List<KSAnnotated> {
        val allViewClasses = resolver.getSymbolsWithAnnotation(ANNOTATION_DATABASE_VIEW_NAME)
        val invalidViewClasses = allViewClasses.filter { !it.validate(enableNewFeatures = false) }.toList()
        val serializableType = resolver.getClassDeclarationByName(ANNOTATION_SERIALIZABLE)!!.asStarProjectedType()
        val transientName = resolver.getClassDeclarationByName(ANNOTATION_TRANSIENT)!!.asStarProjectedType()

        for (classDeclaration in allViewClasses.filter { it.validate(enableNewFeatures = false) } as Sequence<KSClassDeclaration>) {
            val className = classDeclaration.simpleName.asString()
            if (classDeclaration.annotations.all { !it.annotationType.resolve().isAssignableFrom(serializableType) }) {
                environment.logger.error("The class annotated with '@DBView' must be annotated with '@Serializable', but '$className' isn't.", classDeclaration)
                continue
            }
            if (classDeclaration.annotations.any { it.qualifiedName == ANNOTATION_DATABASE_ROW_NAME }) {
                environment.logger.error("The class '$className' is annotated with both '@DBRow' and '@DBView'; it can only be a table or a view.", classDeclaration)
                continue
            }
            val visibility = classDeclaration.getVisibility()
            if (visibility != Visibility.PUBLIC && visibility != Visibility.INTERNAL) {
                environment.logger.error(
                    "The class annotated with '@DBView' must be 'public' or 'internal', but '$className' is '${visibility.name.lowercase()}'.",
                    classDeclaration,
                )
                continue
            }
            val visibilityModifier = if (visibility == Visibility.INTERNAL) "internal " else ""

            // The same properties as a table's: those the serializer writes, in its order
            val propertyList = classDeclaration.getAllProperties().filter { property ->
                property.hasBackingField &&
                    !property.annotations.any { ksAnnotation -> ksAnnotation.annotationType.resolve().isAssignableFrom(transientName) }
            }.toList()

            var isValid = true
            classDeclaration.annotations.filter { it.qualifiedName in CONSTRAINT_ANNOTATIONS }.forEach {
                environment.logger.error("The view '$className' can't be annotated with '@${it.shortName.asString()}': a view has no constraints.", classDeclaration)
                isValid = false
            }
            for (property in propertyList) {
                val propertyName = property.simpleName.asString()
                property.annotations.filter { it.qualifiedName in CONSTRAINT_ANNOTATIONS }.forEach {
                    environment.logger.error(
                        "The property '$propertyName' of view '$className' can't be annotated with '@${it.shortName.asString()}': a view's columns have no constraints.",
                        property,
                    )
                    isValid = false
                }
                if (getClauseElementTypeStr(property) == null) {
                    environment.logger.error(
                        "The property '$propertyName' of '@DBView' class '$className' has the type '${property.type.resolve()}', which no column can hold. " +
                            "Supported types are Byte, Short, Int, Long, Float, Double and their unsigned variants, Boolean, Char, String, ByteArray, enum classes, " +
                            "and type aliases of these. To leave the property out of the view, annotate it with @kotlinx.serialization.Transient.",
                        property,
                    )
                    isValid = false
                }
            }
            if (!isValid)
                continue

            val packageName = classDeclaration.packageName.asString()
            val objectName = "${className}View"
            val viewName = classDeclaration.annotations.find { it.qualifiedName == ANNOTATION_DATABASE_VIEW_NAME }
                ?.arguments?.firstOrNull()?.value?.takeIf { (it as? String)?.isNotBlank() == true } ?: className

            val outputStream = environment.codeGenerator.createNewFile(
                dependencies = classDeclaration.containingFile?.let { Dependencies(true, it) } ?: Dependencies(true),
                packageName = packageName,
                fileName = objectName,
            )
            OutputStreamWriter(outputStream).use { writer ->
                writer.write("package $packageName\n\n")
                writer.write("import com.ctrip.sqllin.dsl.annotation.ColumnNameDslMaker\n")
                writer.write("import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseBlob\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseBoolean\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseEnum\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseNumber\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseString\n")
                writer.write("import com.ctrip.sqllin.dsl.sql.View\n\n")
                // As for table objects, the column properties carry @ColumnNameDslMaker for IntelliJ IDEA's DSL
                // highlighting. View is experimental, and the view object opts in so that its users don't have to.
                writer.write("@Suppress(\"DSL_MARKER_APPLIED_TO_WRONG_TARGET\")\n")
                writer.write("@OptIn(ExperimentalDSLDatabaseAPI::class)\n")
                writer.write("${visibilityModifier}object $objectName : View<$className>(\"$viewName\") {\n\n")
                writer.write("    override fun kSerializer() = $className.serializer()\n\n")
                writer.write("    inline operator fun <R> invoke(block: $objectName.(view: $objectName) -> R): R = this.block(this)\n\n")
                propertyList.forEachIndexed { index, property ->
                    val clauseElementTypeName = checkNotNull(getClauseElementTypeStr(property)) // Rejected above
                    val isNotNull = property.type.resolve().nullability == Nullability.NOT_NULL
                    writer.write("    @ColumnNameDslMaker\n")
                    writer.write("    val ${property.simpleName.asString()}\n")
                    writer.write("        get() = $clauseElementTypeName($className.serializer().descriptor.getElementName($index), this, ${!isNotNull})\n\n")
                }
                writer.write("}\n")
            }
        }
        return invalidViewClasses
    }

    private val KSAnnotation.qualifiedName: String?
        get() = annotationType.resolve().declaration.qualifiedName?.asString()

    /**
     * Maps a property's Kotlin type to the corresponding clause element type name.
     *
     * Handles three categories:
     * - **Typealiases**: Resolves to underlying type and maps to appropriate clause type
     * - **Enum classes**: Maps to `ClauseEnum<EnumType>` for type-safe enum operations
     * - **Standard types**: Maps to `ClauseNumber<Type>`, `ClauseString<Type>`, ClauseBoolean, or ClauseBlob
     *
     * The type argument is the property's type without nullability, as an element knows the type of its values, so
     * that `AS` only selects it into a property of that type.
     *
     * @param property The property declaration to analyze
     * @return The clause type (such as `ClauseNumber<kotlin.Int>` or `ClauseEnum<com.example.Status>`), or null if unsupported
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
     * - Numeric types (Byte, Short, Int, Long, Float, Double, UByte, UShort, UInt, ULong) → `ClauseNumber<Type>`
     * - Text types (Char, String) → `ClauseString<Type>`
     * - Boolean → ClauseBoolean
     * - ByteArray → ClauseBlob
     *
     * Note: Enum types are handled separately by [getClauseElementTypeStr].
     *
     * @param typeName The fully qualified type name to map
     * @return The clause type (such as `ClauseNumber<kotlin.Int>`), or null if unsupported
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
        FullNameCache.UBYTE, -> "ClauseNumber<$typeName>"

        FullNameCache.CHAR,
        FullNameCache.STRING, -> "ClauseString<$typeName>"

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
     * For enum types, converts the enum value to its ordinal before appending, with a safe call only
     * when the enum is nullable.
     *
     * @param elementName The serialized element name
     * @param property The property declaration
     * @param isNotNull Whether the setter's `value` is non-null, as for the SetClause property it belongs to
     * @return The append function call string, or null if unsupported type
     */
    private fun appendFunction(elementName: String, property: KSPropertyDeclaration, isNotNull: Boolean): String? {
        // A safe call on a non-null value is reported as unnecessary, in the module compiling the generated code
        val appendEnum = "appendAny($elementName, value${if (isNotNull) "" else "?"}.ordinal)"
        return when (val declaration = property.type.resolve().declaration) {
            is KSTypeAlias -> {
                val realDeclaration = declaration.type.resolve().declaration
                appendFunctionByTypeName(elementName, realDeclaration.typeName) ?: kotlin.run {
                    if (realDeclaration is KSClassDeclaration && realDeclaration.classKind == ClassKind.ENUM_CLASS)
                        appendEnum
                    else
                        null
                }
            }
            is KSClassDeclaration if declaration.classKind == ClassKind.ENUM_CLASS -> appendEnum
            else -> appendFunctionByTypeName(elementName, declaration.typeName)
        }
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
}