package com.paleblueapps.springadmin.core

import jakarta.persistence.EntityManager
import jakarta.persistence.Lob
import jakarta.persistence.Query
import jakarta.persistence.metamodel.Attribute
import java.math.BigDecimal
import java.lang.reflect.AnnotatedElement
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class AdminCrudService(
    private val entityManager: EntityManager,
    private val registry: AdminEntityRegistry,
) {
    fun list(
        entityKey: String,
        page: Int,
        size: Int,
        sort: String? = null,
        dir: String? = null,
        q: String? = null,
    ): DataPage<Any> {
        val desc = findDescriptorOrThrow(entityKey)

        val safeSort = normalizeSort(desc, sort)
        val orderClause = buildOrderClause(safeSort, dir)
        val search = buildSearchParts(desc, q)

        val queryString =
            buildString {
                append("select x from ")
                append(desc.jpaName)
                append(" x")
                append(search.joinClause)
                append(search.whereClause)
                append(orderClause)
            }

        @Suppress("UNCHECKED_CAST")
        val query =
            entityManager.createQuery(
                queryString,
                desc.javaType as Class<Any>,
            )
        query.applyPaging(page = page, size = size)
        query.applySearchParam(q = q, whereClause = search.whereClause)

        val result = query.resultList

        val countJpql =
            buildString {
                append("select count(*) from ")
                append(desc.jpaName)
                append(" x")
                append(search.joinClause)
                append(search.whereClause)
            }
        val countQuery = entityManager.createQuery(countJpql, java.lang.Long::class.java)
        countQuery.applySearchParam(q, search.whereClause)

        val count = countQuery.singleResult.toLong()
        return DataPage(content = result, page = page, size = size, totalElements = count)
    }

    fun findById(
        entityKey: String,
        idValue: String,
    ): Any? {
        val desc = findDescriptorOrThrow(entityKey)
        val id = convertId(idValue, desc.idType)
        return entityManager.find(desc.javaType, id)
    }

    fun listIds(
        entityKey: String,
        sort: String? = null,
        dir: String? = null,
        q: String? = null,
    ): List<String> {
        val desc = findDescriptorOrThrow(entityKey)
        val safeSort = normalizeSort(desc, sort)
        val orderClause = buildOrderClause(safeSort, dir)
        val search = buildSearchParts(desc, q)

        val queryString =
            buildString {
                append("select distinct x.")
                append(desc.idAttribute)
                append(" from ")
                append(desc.jpaName)
                append(" x")
                append(search.joinClause)
                append(search.whereClause)
                append(orderClause)
            }

        val query = entityManager.createQuery(queryString)
        query.applySearchParam(q = q, whereClause = search.whereClause)
        return query.resultList.mapNotNull { it?.toString() }
    }

    fun listAll(entityKey: String): List<Any> {
        val desc = findDescriptorOrThrow(entityKey)

        @Suppress("UNCHECKED_CAST")
        return entityManager.createQuery(
            "select x from ${desc.jpaName} x order by x.${desc.idAttribute} asc",
            desc.javaType as Class<Any>,
        ).resultList
    }

    @Transactional
    fun deleteById(
        entityKey: String,
        idValue: String,
    ): Boolean = deleteAllByIds(entityKey, listOf(idValue)) > 0

    @Transactional
    fun deleteAllByIds(
        entityKey: String,
        idValues: List<String>,
    ): Int {
        val uniqueIds = idValues.map(String::trim).filter(String::isNotEmpty).distinct()
        var deletedCount = 0

        uniqueIds.forEach { idValue ->
            val entity = findById(entityKey, idValue) ?: return@forEach
            val managed = if (entityManager.contains(entity)) entity else entityManager.merge(entity)
            entityManager.remove(managed)
            deletedCount++
        }

        if (deletedCount > 0) {
            entityManager.flush()
        }

        return deletedCount
    }

    @Transactional
    fun save(entity: Any): Any = entityManager.merge(entity)

    @Transactional
    fun update(
        entityKey: String,
        idValue: String,
        values: Map<String, String>,
    ): Any {
        val desc = findDescriptorOrThrow(entityKey)
        val existing = findById(entityKey, idValue) ?: throw IllegalArgumentException("${desc.displayName} \"$idValue\" does not exist.")
        val constructor =
            desc.javaType.kotlin.primaryConstructor
                ?: throw IllegalStateException("${desc.displayName} must declare a primary constructor to support editing.")

        val attributesByName = desc.detailAttributes.associateBy { it.name }
        val args = mutableMapOf<KParameter, Any?>()

        constructor.parameters.forEach { parameter ->
            val name = parameter.name ?: return@forEach
            val currentValue = readFieldValue(existing, name)
            val attribute = attributesByName[name]

            args[parameter] =
                when {
                    name == desc.idAttribute -> currentValue
                    attribute == null -> currentValue
                    isAssociation(attribute) -> resolveAssociationValue(attribute, values[name], parameter.isOptional, parameter.type.isMarkedNullable)
                    else -> convertSimpleValue(attribute, values[name], parameter.type.isMarkedNullable)
                }
        }

        val updated = constructor.callBy(args)
        return entityManager.merge(updated)
    }

    fun getId(entity: Any): Any? = entityManager.entityManagerFactory.persistenceUnitUtil.getIdentifier(entity)

    private fun findDescriptorOrThrow(entityKey: String): AdminEntityDescriptor =
        registry.get(entityKey) ?: throw IllegalArgumentException("Unknown entity: $entityKey")

    private fun normalizeSort(
        desc: AdminEntityDescriptor,
        sort: String?,
    ): String? =
        sort?.takeIf { candidate ->
            candidate.isNotBlank() && desc.attributes.any { it.name == candidate }
        }

    private fun buildOrderClause(
        sort: String?,
        dir: String?,
    ): String {
        if (sort.isNullOrBlank()) return ""
        val direction = if (dir.equals("desc", ignoreCase = true)) "desc" else "asc"
        return " order by x.$sort $direction"
    }

    private fun buildSearchParts(
        desc: AdminEntityDescriptor,
        q: String?,
    ): SearchParts {
        if (q.isNullOrBlank()) return SearchParts()

        val rootFields = rootSearchableFields(desc)
        val assoc = buildAssocSearch(desc)

        val orParts = mutableListOf<String>()
        if (rootFields.isNotEmpty()) {
            orParts += rootFields.map { "lower(x.$it) like :q" }
        }
        if (assoc.joinClause.isNotBlank() && assoc.predicates.isNotEmpty()) {
            orParts += assoc.predicates.map { "lower(${it.alias}.${it.field}) like :q" }
        }
        val where = if (orParts.isNotEmpty()) " where (" + orParts.joinToString(" or ") + ")" else ""
        return SearchParts(joinClause = assoc.joinClause, whereClause = where)
    }

    private fun rootSearchableFields(desc: AdminEntityDescriptor): Set<String> =
        buildSet {
            (desc.attributes + desc.detailAttributes).forEach { attr ->
                if (isStringNonLob(attr)) add(attr.name)
            }
            if (desc.idType == String::class.java) add(desc.idAttribute)
        }

    private fun buildAssocSearch(desc: AdminEntityDescriptor): AssocSearch {
        val predicates = mutableSetOf<AssocPredicate>()
        val joinedAliases = mutableSetOf<String>()
        val joinBuilder = StringBuilder()

        desc.detailAttributes.forEach { attr ->
            when (attr.persistentAttributeType) {
                Attribute.PersistentAttributeType.MANY_TO_ONE,
                Attribute.PersistentAttributeType.ONE_TO_ONE,
                -> {
                    val alias = "j_${attr.name}"
                    if (joinedAliases.add(alias)) {
                        joinBuilder.append(" left join x.${attr.name} $alias")
                    }
                    val targetDesc = registry.getByJavaType(attr.javaType) ?: return@forEach
                    val targetFields =
                        buildSet {
                            targetDesc.detailAttributes.forEach { ta ->
                                if (isStringNonLob(ta)) add(ta.name)
                            }
                            if (targetDesc.idType == String::class.java) add(targetDesc.idAttribute)
                        }
                    targetFields.forEach { field -> predicates += AssocPredicate(alias, field) }
                }

                else -> Unit
            }
        }

        return AssocSearch(joinClause = joinBuilder.toString(), predicates = predicates)
    }

    private fun isStringNonLob(attr: Attribute<*, *>): Boolean {
        if (attr.javaType != String::class.java) return false
        val annotated = attr.javaMember as? AnnotatedElement
        return annotated?.getAnnotation(Lob::class.java) == null
    }

    private fun isAssociation(attr: Attribute<*, *>): Boolean =
        attr.persistentAttributeType in
            setOf(
                Attribute.PersistentAttributeType.MANY_TO_ONE,
                Attribute.PersistentAttributeType.ONE_TO_ONE,
            )

    private fun resolveAssociationValue(
        attr: Attribute<*, *>,
        rawValue: String?,
        isOptional: Boolean,
        isNullable: Boolean,
    ): Any? {
        val normalized = rawValue?.trim().orEmpty()
        if (normalized.isEmpty()) {
            if (isOptional || isNullable) return null
            throw IllegalArgumentException("${attr.name} is required.")
        }

        val targetDesc = registry.getByJavaType(attr.javaType)
            ?: throw IllegalStateException("Unknown association target for ${attr.name}.")

        return findById(targetDesc.entityName, normalized)
            ?: throw IllegalArgumentException("${targetDesc.displayName} \"$normalized\" does not exist.")
    }

    private fun convertSimpleValue(
        attr: Attribute<*, *>,
        rawValue: String?,
        isNullable: Boolean,
    ): Any? {
        val normalized = rawValue?.trim()
        if (normalized.isNullOrEmpty()) {
            if (attr.javaType == String::class.java) return rawValue ?: ""
            if (isNullable) return null
            throw IllegalArgumentException("${attr.name} is required.")
        }

        return try {
            when (attr.javaType.kotlin) {
                String::class -> rawValue ?: ""
                Int::class -> normalized.toInt()
                Long::class -> normalized.toLong()
                Short::class -> normalized.toShort()
                Byte::class -> normalized.toByte()
                Double::class -> normalized.toDouble()
                Float::class -> normalized.toFloat()
                Boolean::class -> normalized.toBooleanStrict()
                BigDecimal::class -> normalized.toBigDecimal()
                LocalDate::class -> LocalDate.parse(normalized)
                LocalDateTime::class -> LocalDateTime.parse(normalized)
                OffsetDateTime::class -> OffsetDateTime.parse(normalized)
                UUID::class -> UUID.fromString(normalized)
                else -> {
                    if (attr.javaType.isEnum) {
                        enumValue(attr.javaType.kotlin, normalized)
                    } else {
                        rawValue
                    }
                }
            }
        } catch (ex: Exception) {
            throw IllegalArgumentException("Invalid value for ${attr.name}: $rawValue")
        }
    }

    private fun readFieldValue(
        target: Any,
        fieldName: String,
    ): Any? =
        try {
            val field = target.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(target)
        } catch (_: Exception) {
            null
        }

    @Suppress("UNCHECKED_CAST")
    private fun enumValue(
        type: KClass<*>,
        rawValue: String,
    ): Any =
        java.lang.Enum.valueOf(
            type.java as Class<out Enum<*>>,
            rawValue,
        )

    private fun Query.applyPaging(
        page: Int,
        size: Int,
    ) {
        if (size <= 0) return
        firstResult = page.coerceAtLeast(0) * size
        maxResults = size
    }

    private fun Query.applySearchParam(
        q: String?,
        whereClause: String,
    ) {
        if (q.isNullOrBlank() || !whereClause.isNotBlank()) return
        setParameter("q", "%" + q.trim().lowercase() + "%")
    }

    private fun convertId(
        value: String,
        idType: Class<*>,
    ): Any =
        when (idType) {
            java.lang.Long::class.java, Long::class.java -> value.toLong()
            java.lang.Integer::class.java, Int::class.java -> value.toInt()
            java.util.UUID::class.java, UUID::class.java -> UUID.fromString(value)
            java.lang.String::class.java, String::class.java -> value
            else -> throw UnsupportedOperationException("Unsupported ID type: ${idType.name}")
        }
}

private data class SearchParts(
    val joinClause: String = "",
    val whereClause: String = "",
)

private data class AssocPredicate(
    val alias: String,
    val field: String,
)

private data class AssocSearch(
    val joinClause: String,
    val predicates: Set<AssocPredicate>,
)
