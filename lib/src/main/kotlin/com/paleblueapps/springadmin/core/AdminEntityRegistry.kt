package com.paleblueapps.springadmin.core

import jakarta.persistence.EntityManager
import jakarta.persistence.metamodel.Attribute
import java.util.Locale

class AdminEntityRegistry(
    private val entityManager: EntityManager,
) {
    private val entitiesByKey: Map<String, AdminEntityDescriptor> by lazy { discoverEntities() }

    fun all(): List<AdminEntityDescriptor> =
        entitiesByKey.values.sortedBy { it.displayName.lowercase(Locale.getDefault()) }

    fun get(key: String): AdminEntityDescriptor? = entitiesByKey[key]

    private fun discoverEntities(): Map<String, AdminEntityDescriptor> =
        mutableMapOf<String, AdminEntityDescriptor>().apply {
            val metamodel = entityManager.metamodel

            metamodel.entities.forEach { et ->
                val javaType = et.javaType
                val jpaName = et.name
                val simple = javaType.simpleName
                val key = simple.replaceFirstChar { it.lowercase(Locale.ENGLISH) }
                val idAttr = et.singularAttributes.find { it.isId }
                    ?: throw IllegalStateException("Entity ${et.name} must have an @Id attribute.")
                val idName = idAttr.name
                val idType = idAttr.javaType

                // Attributes for list views: BASIC and EMBEDDED only
                val listAttributes = et.attributes.filter { attr ->
                    when (attr.persistentAttributeType) {
                        Attribute.PersistentAttributeType.BASIC,
                        Attribute.PersistentAttributeType.EMBEDDED -> true
                        else -> false
                    }
                }

                // Attributes for detail views: include singular associations (MANY_TO_ONE, ONE_TO_ONE)
                val detailAttributes = et.attributes.filter { attr ->
                    when (attr.persistentAttributeType) {
                        Attribute.PersistentAttributeType.BASIC,
                        Attribute.PersistentAttributeType.EMBEDDED,
                        Attribute.PersistentAttributeType.MANY_TO_ONE,
                        Attribute.PersistentAttributeType.ONE_TO_ONE -> true
                        else -> false
                    }
                }

                val desc = AdminEntityDescriptor(
                    entityName = key,
                    displayName = simple,
                    jpaName = jpaName,
                    javaType = javaType,
                    idAttribute = idName,
                    idType = idType,
                    attributes = listAttributes,
                    detailAttributes = detailAttributes,
                )
                put(key, desc)
            }
        }.toMap()
}
