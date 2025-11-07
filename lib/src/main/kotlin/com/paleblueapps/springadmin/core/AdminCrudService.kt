package com.paleblueapps.springadmin.core

import jakarta.persistence.EntityManager
import jakarta.persistence.Lob
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
        val desc =
            registry.get(entityKey) ?: throw IllegalArgumentException("Unknown entity: $entityKey")

        val safeSort =
            sort?.takeIf {
                it.isNotBlank() && desc.attributes.any { a -> a.name == sort }
            }
        val direction = if (dir?.equals("desc", ignoreCase = true) == true) "desc" else "asc"
        val orderClause =
            if (safeSort != null) {
                " order by x.$safeSort $direction"
            } else {
                ""
            }

        // Build dynamic JOINs and WHERE when searching, including foreign tables (singular associations)
        var joinClause = ""
        val whereClause =
            if (!q.isNullOrBlank()) {
                // Searchable fields on root entity
                val rootSearchableAttrNames: Set<String> =
                    buildSet {
                        (desc.attributes + desc.detailAttributes).forEach { attr ->
                            if (attr.javaType == String::class.java) {
                                val member = attr.javaMember
                                val annotated = member as? AnnotatedElement
                                val hasLob = annotated?.getAnnotation(Lob::class.java) != null
                                if (!hasLob) add(attr.name)
                            }
                        }
                        if (desc.idType == String::class.java) add(desc.idAttribute)
                    }

                // Collect JOINs for singular associations and their searchable fields
                data class AssocPredicate(val alias: String, val field: String)
                val assocPredicates = mutableSetOf<AssocPredicate>()
                val joinedAliases = mutableSetOf<String>()
                desc.detailAttributes.forEach { attr ->
                    val pat = attr.persistentAttributeType.name
                    if (pat == "MANY_TO_ONE" || pat == "ONE_TO_ONE") {
                        val alias = "j_" + attr.name
                        if (joinedAliases.add(alias)) {
                            // register join
                            joinClause += " left join x.${attr.name} $alias"
                        }
                        val targetType = attr.javaType
                        val targetDesc = registry.getByJavaType(targetType)
                        if (targetDesc != null) {
                            // target searchable string fields (exclude LOB)
                            val targetStringFields = buildSet {
                                (targetDesc.attributes + targetDesc.detailAttributes).forEach { ta ->
                                    if (ta.javaType == String::class.java) {
                                        val m = ta.javaMember
                                        val ann = m as? AnnotatedElement
                                        val hasLob = ann?.getAnnotation(Lob::class.java) != null
                                        if (!hasLob) add(ta.name)
                                    }
                                }
                                if (targetDesc.idType == String::class.java) add(targetDesc.idAttribute)
                            }
                            targetStringFields.forEach { f -> assocPredicates.add(AssocPredicate(alias, f)) }
                        }
                    }
                }

                // Build ORs across root and associations
                val orsParts = mutableListOf<String>()
                if (rootSearchableAttrNames.isNotEmpty()) {
                    orsParts += rootSearchableAttrNames.map { "lower(x.$it) like :q" }
                }
                if (joinClause.isNotBlank() && assocPredicates.isNotEmpty()) {
                    orsParts += assocPredicates.map { ap -> "lower(${ap.alias}.${ap.field}) like :q" }
                }
                if (orsParts.isNotEmpty()) {
                    " where (" + orsParts.joinToString(" or ") + ")"
                } else {
                    ""
                }
            } else {
                ""
            }

        val queryString =
            buildString {
                append("select x from ")
                append(desc.jpaName)
                append(" x")
                append(joinClause)
                append(whereClause)
                append(orderClause)
            }

        @Suppress("UNCHECKED_CAST")
        val query =
            entityManager.createQuery(
                // qlString =
                queryString,
                // resultClass =
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
        val countJpql = buildString {
            append("select count(*) from ")
            append(desc.jpaName)
            append(" x")
            append(joinClause)
            append(whereClause)
        }
        val countQuery =
            entityManager.createQuery(
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

    fun findById(
        entityKey: String,
        idValue: String,
    ): Any? {
        val desc =
            registry.get(entityKey)
                ?: throw IllegalArgumentException("Unknown entity: $entityKey")

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
