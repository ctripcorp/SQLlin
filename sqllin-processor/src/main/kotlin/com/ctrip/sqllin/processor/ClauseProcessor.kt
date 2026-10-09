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
 *   - CHECK constraints of columns and of the table, optionally named
 * - **Type support**:
 *   - All Kotlin primitive types and unsigned variants
 *   - String, Char, Boolean, ByteArray
 *   - Enum classes (stored as integers)
 *   - Typealiases of supported types
 *
 * A `@DBRow` class annotated with [@Fts4][com.ctrip.sqllin.dsl.annotation.Fts4] or
 * [@Fts3][com.ctrip.sqllin.dsl.annotation.Fts3] gets an `FtsTable` object instead, whose statement is a
 * `CREATE VIRTUAL TABLE`, and a class annotated with [@DBView][com.ctrip.sqllin.dsl.annotation.DBView] a `View` object.
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
 * @see com.ctrip.sqllin.dsl.annotation.Check
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
        const val ANNOTATION_FTS4 = "$ANNOTATION_PACKAGE.Fts4"
        const val ANNOTATION_FTS3 = "$ANNOTATION_PACKAGE.Fts3"
        const val ANNOTATION_PRIMARY_KEY = "$ANNOTATION_PACKAGE.PrimaryKey"
        const val ANNOTATION_CHECK = "$ANNOTATION_PACKAGE.Check"
        val SQL_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

        /** The names SQLite gives the rowid; an FTS table also calls it docid. */
        val ROW_ID_NAMES = setOf("rowid", "oid", "_rowid_", "docid")

        /** The annotations that declare constraints, which only a table's columns can have. */
        val CONSTRAINT_ANNOTATIONS = listOf(
            "PrimaryKey", "CompositePrimaryKey", "CollateNoCase", "Unique", "CompositeUnique",
            "ForeignKeyGroup", "References", "ForeignKey", "Default", "Check",
        ).map { "$ANNOTATION_PACKAGE.$it" }.toSet()
        const val ANNOTATION_SERIALIZABLE = "kotlinx.serialization.Serializable"
        const val ANNOTATION_TRANSIENT = "kotlinx.serialization.Transient"

        /**
         * The getter of a column's property in `SET {}` only returns a placeholder, as `0` or `""`, so reading it, as
         * in `age = age + 1`, would set the column to a value computed from the placeholder. It is an error to read it.
         */
        const val SET_CLAUSE_GETTER_DEPRECATION =
            "        @Deprecated(\"A column in SET {} can only be assigned: reading it gives a placeholder, not the column's value. To set a column to an expression of columns, use SET(listOf(expression AS Row::property)).\", level = DeprecationLevel.ERROR)\n"
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

            val ftsAnnotations = classDeclaration.annotations.filter { it.qualifiedName == ANNOTATION_FTS4 || it.qualifiedName == ANNOTATION_FTS3 }.toList()
            if (ftsAnnotations.isNotEmpty()) {
                processFtsTable(classDeclaration, ftsAnnotations, propertyList, tableName as String, visibilityModifier)
                continue
            }

            if (!validateChecks(classDeclaration, propertyList))
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

                        // Before @References, whose constraint name SQLite would give the CHECK constraints after it
                        appendColumnChecks(this, property)

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
                    writer.write(SET_CLAUSE_GETTER_DEPRECATION)
                    writer.write("        get() = ${getSetClauseGetterValue(property)}\n")
                    writer.write("        set(value) = ${appendFunction(elementName, property, isNotNull)}\n\n")
                }

                columnConstraintParser.generateCodeForPrimaryKey(writer, createSQLBuilder)
                foreignKeyParser.generateCodeForForeignKey(createSQLBuilder)
                for (check in classDeclaration.checkConstraints()) {
                    createSQLBuilder.append(',')
                    check.appendTo(createSQLBuilder)
                }
                createSQLBuilder.append(')')

                // A CHECK expression or a DEFAULT value can hold characters that a Kotlin string literal escapes
                writer.write("    override val createSQL = \"${kotlinStringContent(createSQLBuilder.toString())}\"\n")

                writer.write("}\n")
            }
        }
        // @Fts4 and @Fts3 only describe the table of a @DBRow class, without which nothing would be generated for them
        for (ftsAnnotationName in listOf(ANNOTATION_FTS4, ANNOTATION_FTS3)) {
            resolver.getSymbolsWithAnnotation(ftsAnnotationName)
                .filter { symbol -> symbol.annotations.none { it.qualifiedName == ANNOTATION_DATABASE_ROW_NAME } }
                .forEach {
                    val annotationName = ftsAnnotationName.substringAfterLast('.')
                    environment.logger.error("The class annotated with '@$annotationName' must also be annotated with '@DBRow', which makes it a table.", it)
                }
        }
        return invalidateDBRowClasses + processViews(resolver)
    }

    /**
     * Generates the table object of a `@DBRow` class annotated with [@Fts4][com.ctrip.sqllin.dsl.annotation.Fts4] or
     * [@Fts3][com.ctrip.sqllin.dsl.annotation.Fts3]: an `FtsTable`, created with `CREATE VIRTUAL TABLE`.
     *
     * Its columns are the class's `String` properties, as FTS indexes text, without types or constraints, which an FTS
     * table doesn't have. A `@PrimaryKey` property of type `Long` named `rowid` or `docid` isn't a column but the row's
     * id, which `Long?` leaves for SQLite to assign, as for a table's `INTEGER PRIMARY KEY`.
     */
    private fun processFtsTable(
        classDeclaration: KSClassDeclaration,
        ftsAnnotations: List<KSAnnotation>,
        propertyList: List<KSPropertyDeclaration>,
        tableName: String,
        visibilityModifier: String,
    ) {
        val className = classDeclaration.simpleName.asString()
        var isValid = true
        fun error(message: String, symbol: KSNode) {
            environment.logger.error(message, symbol)
            isValid = false
        }

        if (ftsAnnotations.size > 1)
            error("The class '$className' is annotated with both '@Fts3' and '@Fts4'; its FTS table can only be one of them.", classDeclaration)
        val ftsAnnotation = ftsAnnotations.first()
        val isFts4 = ftsAnnotation.qualifiedName == ANNOTATION_FTS4
        classDeclaration.annotations.filter { it.qualifiedName in CONSTRAINT_ANNOTATIONS }.forEach {
            error("The FTS table '$className' can't be annotated with '@${it.shortName.asString()}': an FTS table has no constraints.", classDeclaration)
        }

        var rowIdProperty: KSPropertyDeclaration? = null
        val columns = ArrayList<String>()
        for (property in propertyList) {
            val propertyName = property.simpleName.asString()
            val constraints = property.annotations.filter { it.qualifiedName in CONSTRAINT_ANNOTATIONS }.toList()
            val primaryKey = constraints.find { it.qualifiedName == ANNOTATION_PRIMARY_KEY }
            if (propertyName.lowercase() in ROW_ID_NAMES) {
                val rules = "The rowid of an FTS table is read and written through a @PrimaryKey property of type Long or Long?, named 'rowid' or 'docid'"
                when {
                    propertyName != "rowid" && propertyName != "docid" ->
                        error("The property '$propertyName' of FTS table '$className' is named as the rowid. $rules.", property)
                    primaryKey == null ->
                        error("The property '$propertyName' of FTS table '$className' has to be annotated with '@PrimaryKey'. $rules.", property)
                    resolvedTypeName(property) != FullNameCache.LONG ->
                        error("The property '$propertyName' of FTS table '$className' has the type '${property.type.resolve()}'. $rules.", property)
                    primaryKey.arguments.any { it.name?.asString() == "autoIncrement" && it.value == true } ->
                        error("The rowid of FTS table '$className' can't be AUTOINCREMENT, which only a table's INTEGER PRIMARY KEY can be.", property)
                    rowIdProperty != null ->
                        error("The FTS table '$className' has two rowid properties, '${rowIdProperty.simpleName.asString()}' and '$propertyName'.", property)
                }
                rowIdProperty = property
                (constraints - primaryKey).filterNotNull().forEach {
                    error("The rowid '$propertyName' of FTS table '$className' can't be annotated with '@${it.shortName.asString()}': an FTS table has no constraints.", property)
                }
                continue
            }
            if (primaryKey != null)
                error("The property '$propertyName' of FTS table '$className' can't be its primary key, which is its rowid. To read and write the rowid, give the class a @PrimaryKey property of type Long or Long? named 'rowid' or 'docid'.", property)
            (constraints - primaryKey).filterNotNull().forEach {
                error("The property '$propertyName' of FTS table '$className' can't be annotated with '@${it.shortName.asString()}': an FTS table's columns have no constraints.", property)
            }
            if (resolvedTypeName(property) != FullNameCache.STRING)
                error("The property '$propertyName' of FTS table '$className' has the type '${property.type.resolve()}', but FTS indexes text, so its columns are String or String?.", property)
            columns.add(propertyName)
        }
        if (columns.isEmpty())
            error("The FTS table '$className' has no columns. Its columns are its String or String? properties.", classDeclaration)

        val arguments = ftsAnnotation.arguments.associate { it.name?.asString() to it.value }
        val options = ArrayList<String>()
        val tokenizer = when (val value = arguments["tokenizer"]) {
            is KSClassDeclaration -> value.simpleName.asString()
            is KSType -> value.declaration.simpleName.asString()
            null -> "SIMPLE"
            else -> value.toString().substringAfterLast('.')
        }.lowercase()
        val tokenizerArgs = (arguments["tokenizerArgs"] as? List<*>).orEmpty().map { it.toString() }
        if (tokenizer != "simple" || tokenizerArgs.isNotEmpty())
            options.add((listOf(tokenizer) + tokenizerArgs.map { "\"${it.replace("\"", "\"\"")}\"" }).joinToString(" ", "tokenize="))
        if (isFts4) {
            val prefix = (arguments["prefix"] as? List<*>).orEmpty().map { it as Int }
            if (prefix.any { it <= 0 })
                error("The prefix lengths of FTS table '$className' have to be positive, but they are ${prefix.joinToString()}.", classDeclaration)
            if (prefix.isNotEmpty())
                options.add("prefix=\"${prefix.joinToString(",")}\"")
            for (column in (arguments["notIndexed"] as? List<*>).orEmpty().map { it.toString() }) {
                if (column !in columns)
                    error("The column '$column' that FTS table '$className' doesn't index isn't one of its columns, ${columns.joinToString { "'$it'" }}.", classDeclaration)
                options.add("notindexed=$column")
            }
        }
        if (!isValid)
            return

        val createSQL = (columns + options).joinToString(",", "CREATE VIRTUAL TABLE $tableName USING ${if (isFts4) "fts4" else "fts3"}(", ")")
        val packageName = classDeclaration.packageName.asString()
        val objectName = "${className}Table"
        val outputStream = environment.codeGenerator.createNewFile(
            dependencies = classDeclaration.containingFile?.let { Dependencies(true, it) } ?: Dependencies(true),
            packageName = packageName,
            fileName = objectName,
        )
        OutputStreamWriter(outputStream).use { writer ->
            writer.write("package $packageName\n\n")
            writer.write("import com.ctrip.sqllin.dsl.annotation.ColumnNameDslMaker\n")
            writer.write("import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI\n")
            writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseNumber\n")
            writer.write("import com.ctrip.sqllin.dsl.sql.clause.ClauseString\n")
            writer.write("import com.ctrip.sqllin.dsl.sql.clause.SetClause\n")
            writer.write("import com.ctrip.sqllin.dsl.sql.FtsTable\n")
            writer.write("import com.ctrip.sqllin.dsl.sql.PrimaryKeyInfo\n\n")
            // As for table objects, the column properties carry @ColumnNameDslMaker for IntelliJ IDEA's DSL
            // highlighting. FtsTable is experimental, and the table object opts in so that its users don't have to.
            writer.write("@Suppress(\"DSL_MARKER_APPLIED_TO_WRONG_TARGET\")\n")
            writer.write("@OptIn(ExperimentalDSLDatabaseAPI::class)\n")
            writer.write("${visibilityModifier}object $objectName : FtsTable<$className>(\"$tableName\") {\n\n")
            writer.write("    override fun kSerializer() = $className.serializer()\n\n")
            writer.write("    inline operator fun <R> invoke(block: $objectName.(table: $objectName) -> R): R = this.block(this)\n\n")
            propertyList.forEachIndexed { index, property ->
                val propertyName = property.simpleName.asString()
                val elementName = "$className.serializer().descriptor.getElementName($index)"
                val isNotNull = property.type.resolve().nullability == Nullability.NOT_NULL
                writer.write("    @ColumnNameDslMaker\n")
                writer.write("    val $propertyName\n")
                writer.write("        get() = ${checkNotNull(getClauseElementTypeStr(property))}($elementName, this, ${!isNotNull})\n\n")
                writer.write("    @ColumnNameDslMaker\n")
                writer.write("    var SetClause<$className>.$propertyName: ${property.typeName}${if (isNotNull) "" else "?"}\n")
                writer.write(SET_CLAUSE_GETTER_DEPRECATION)
                writer.write("        get() = ${getSetClauseGetterValue(property)}\n")
                writer.write("        set(value) = ${appendFunction(elementName, property, isNotNull)}\n\n")
            }
            val rowId = rowIdProperty
            if (rowId == null) {
                writer.write("    override val primaryKeyInfo = null\n\n")
            } else {
                writer.write("    override val primaryKeyInfo = PrimaryKeyInfo(\n")
                writer.write("        primaryKeyName = \"${rowId.simpleName.asString()}\",\n")
                writer.write("        isAutomaticIncrement = false,\n")
                writer.write("        isGeneratedByDatabase = ${rowId.type.resolve().nullability != Nullability.NOT_NULL},\n")
                writer.write("        compositePrimaryKeys = null,\n")
                writer.write("    )\n\n")
            }
            writer.write("    override val createSQL = \"${kotlinStringContent(createSQL)}\"\n")
            writer.write("}\n")
        }
    }

    /**
     * A `CHECK` constraint, read from a [@Check][com.ctrip.sqllin.dsl.annotation.Check] annotation.
     */
    private class CheckConstraint(val expression: String, val constraintName: String) {

        /**
         * Appends `CHECK (expression)` to [builder], after `CONSTRAINT name ` when the constraint is named.
         */
        fun appendTo(builder: StringBuilder) {
            if (constraintName.isNotEmpty()) {
                builder.append("CONSTRAINT ")
                builder.append(constraintName)
                builder.append(' ')
            }
            builder.append("CHECK (")
            builder.append(expression)
            builder.append(')')
        }
    }

    /**
     * Returns the `CHECK` constraints that [@Check][com.ctrip.sqllin.dsl.annotation.Check] annotations declare on
     * this class or property, in their order.
     */
    private fun KSAnnotated.checkConstraints(): List<CheckConstraint> =
        annotations.filter { it.qualifiedName == ANNOTATION_CHECK }.map { annotation ->
            val arguments = annotation.arguments.associate { it.name?.asString() to it.value }
            CheckConstraint(
                expression = (arguments["expression"] as? String).orEmpty().trim(),
                constraintName = (arguments["constraintName"] as? String).orEmpty().trim(),
            )
        }.toList()

    /**
     * Appends the `CHECK` constraints of [property]'s column to [createSQLBuilder]: the unnamed ones first, as SQLite
     * gives a column's constraint the name of the named one before it, and would report it under that name.
     */
    private fun appendColumnChecks(createSQLBuilder: StringBuilder, property: KSPropertyDeclaration) {
        for (check in property.checkConstraints().sortedBy { it.constraintName.isNotEmpty() }) {
            createSQLBuilder.append(' ')
            check.appendTo(createSQLBuilder)
        }
    }

    /**
     * Reports the [@Check][com.ctrip.sqllin.dsl.annotation.Check] annotations of a `@DBRow` class and its properties
     * that would make an invalid table: without an expression, with a name that isn't an SQL identifier, or with the
     * name of another of the table's CHECK constraints, which would make SQLite's report of a failed one ambiguous.
     *
     * @return Whether all of them are valid
     */
    private fun validateChecks(classDeclaration: KSClassDeclaration, propertyList: List<KSPropertyDeclaration>): Boolean {
        val className = classDeclaration.simpleName.asString()
        val names = HashSet<String>()
        var isValid = true
        for (symbol in listOf<KSAnnotated>(classDeclaration) + propertyList) {
            for (check in symbol.checkConstraints()) {
                val name = check.constraintName
                val message = when {
                    check.expression.isEmpty() -> "A @Check of '$className' has no expression."
                    name.isNotEmpty() && !SQL_IDENTIFIER.matches(name) ->
                        "The constraint name '$name' of a @Check of '$className' isn't an SQL identifier: it can only contain letters, digits and underscores, and can't start with a digit."
                    name.isNotEmpty() && !names.add(name) ->
                        "The table of '$className' has two CHECK constraints named '$name'. Their names have to differ, as SQLite reports the name of the one a row fails."
                    else -> continue
                }
                environment.logger.error(message, symbol)
                isValid = false
            }
        }
        return isValid
    }

    /**
     * Returns the qualified name of a property's type, without nullability, through a type alias.
     */
    private fun resolvedTypeName(property: KSPropertyDeclaration): String? = when (val declaration = property.type.resolve().declaration) {
        is KSTypeAlias -> declaration.type.resolve().declaration.typeName
        else -> declaration.typeName
    }

    /**
     * Escapes [string] to be the content of a Kotlin string literal.
     */
    private fun kotlinStringContent(string: String): String =
        string.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

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