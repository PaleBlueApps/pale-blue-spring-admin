package com.paleblueapps.springadmin.core

import jakarta.persistence.EntityManager
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
        val desc =
            registry.get(entityKey) ?: throw IllegalArgumentException("Unknown entity: $entityKey")

        val safeSort = sort?.takeIf {
            it.isNotBlank() && desc.attributes.any { a -> a.name == sort }
        }
        val direction = if (dir?.equals("desc", ignoreCase = true) == true) "desc" else "asc"
        val orderClause = if (safeSort != null) {
            " order by x.$safeSort $direction"
        } else {
            ""
        }

        val whereClause = if (!q.isNullOrBlank()) {
            // Build a simple LIKE filter across string attributes
            val stringAttrs = desc.attributes.filter {
                it.javaType == String::class.java
            }.map { it.name }
            if (stringAttrs.isNotEmpty()) {
                val ors = stringAttrs.joinToString(" or ") { "lower(x.$it) like :q" }
                " where ($ors)"
            } else {
                ""
            }
        } else {
            ""
        }

        val queryString = buildString {
            append("select x from ")
            append(desc.jpaName)
            append(" x")
            append(whereClause)
            append(orderClause)
        }

        @Suppress("UNCHECKED_CAST")
        val query = entityManager.createQuery(
            /* qlString = */
            queryString,
            /* resultClass = */
            desc.javaType as Class<Any>,
        )
        if (size > 0) {
            query.firstResult = page.coerceAtLeast(0) * size
            query.maxResults = size
        }
        if (!q.isNullOrBlank() && whereClause.isNotBlank()) {
            query.setParameter("q", "%" + q.trim().lowercase() + "%")
        }
        val result = query.resultList
        val countJpql = "select count(x) from " + desc.jpaName + " x" + whereClause
        val countQuery = entityManager.createQuery(
            countJpql,
            java.lang.Long::class.java,
        )
        if (!q.isNullOrBlank() && whereClause.isNotBlank()) {
            countQuery.setParameter("q", "%" + q.trim().lowercase() + "%")
        }
        val countJava = countQuery.singleResult
        val count = countJava.toLong()
        return DataPage(content = result, page = page, size = size, totalElements = count)
    }

    fun findById(entityKey: String, idValue: String): Any? {
        val desc = registry.get(entityKey)
            ?: throw IllegalArgumentException("Unknown entity: $entityKey")

        val id = convertId(idValue, desc.idType)
        return entityManager.find(desc.javaType, id)
    }

    fun deleteById(entityKey: String, idValue: String) {
        val entity = findById(entityKey, idValue) ?: return
        val managed = if (entityManager.contains(entity)) entity else entityManager.merge(entity)
        entityManager.remove(managed)
    }

    fun save(entity: Any): Any = entityManager.merge(entity)

    fun getId(entity: Any): Any? =
        entityManager.entityManagerFactory.persistenceUnitUtil.getIdentifier(entity)

    private fun convertId(value: String, idType: Class<*>): Any = when (idType) {
        java.lang.Long::class.java, Long::class.java -> value.toLong()
        java.lang.Integer::class.java, Int::class.java -> value.toInt()
        java.util.UUID::class.java, UUID::class.java -> UUID.fromString(value)
        java.lang.String::class.java, String::class.java -> value
        else -> throw UnsupportedOperationException("Unsupported ID type: ${idType.name}")
    }
}
