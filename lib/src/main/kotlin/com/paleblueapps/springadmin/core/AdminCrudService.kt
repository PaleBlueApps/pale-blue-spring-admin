package com.paleblueapps.springadmin.core

import jakarta.persistence.EntityManager
import jakarta.persistence.Lob
import jakarta.persistence.Query
import jakarta.persistence.metamodel.Attribute
import java.lang.reflect.AnnotatedElement
import java.util.UUID

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

    fun deleteById(
        entityKey: String,
        idValue: String,
    ) {
        val entity = findById(entityKey, idValue) ?: return
        val managed = if (entityManager.contains(entity)) entity else entityManager.merge(entity)
        entityManager.remove(managed)
    }

    fun save(entity: Any): Any = entityManager.merge(entity)

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
