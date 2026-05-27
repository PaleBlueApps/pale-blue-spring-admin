package com.paleblueapps.springadmin.core

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceUnitUtil
import jakarta.persistence.TypedQuery
import jakarta.persistence.metamodel.Attribute
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDateTime
import kotlin.test.assertEquals

class AdminCrudServiceTest {
    @Test
    fun `sorts temporal fields in memory so mixed storage values stay chronological`() {
        val entityManager = mock(EntityManager::class.java)
        val registry = mock(AdminEntityRegistry::class.java)
        val query = mock(TypedQuery::class.java) as TypedQuery<Any>
        val entityManagerFactory = mock(EntityManagerFactory::class.java)
        val persistenceUnitUtil = mock(PersistenceUnitUtil::class.java)
        val createdAtAttribute = mock(Attribute::class.java) as Attribute<*, *>

        `when`(createdAtAttribute.name).thenReturn("createdAt")
        `when`(createdAtAttribute.javaType).thenReturn(LocalDateTime::class.java)

        val descriptor =
            AdminEntityDescriptor(
                entityName = "temporalThing",
                displayName = "TemporalThing",
                jpaName = "TemporalThing",
                javaType = TemporalThing::class.java,
                idAttribute = "id",
                idType = Int::class.java,
                idGenerated = false,
                attributes = listOf(createdAtAttribute),
                detailAttributes = listOf(createdAtAttribute),
            )

        val unsorted =
            listOf(
                TemporalThing(2, LocalDateTime.of(2026, 1, 27, 12, 0)),
                TemporalThing(1, LocalDateTime.of(2026, 1, 25, 19, 50)),
                TemporalThing(3, LocalDateTime.of(2026, 1, 26, 1, 15)),
            )

        `when`(registry.get("temporalThing")).thenReturn(descriptor)
        `when`(
            entityManager.createQuery(
                "select x from TemporalThing x",
                TemporalThing::class.java as Class<Any>,
            ),
        ).thenReturn(query)
        `when`(query.resultList).thenReturn(unsorted)
        `when`(entityManager.entityManagerFactory).thenReturn(entityManagerFactory)
        `when`(entityManagerFactory.persistenceUnitUtil).thenReturn(persistenceUnitUtil)
        `when`(persistenceUnitUtil.getIdentifier(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as TemporalThing).id
        }

        val service = AdminCrudService(entityManager, registry)

        val sortedPage = service.list("temporalThing", page = 0, size = 10, sort = "createdAt", dir = "asc")
        val sortedIds = sortedPage.content.map { (it as TemporalThing).id }

        val selectedIds = service.listIds("temporalThing", sort = "createdAt", dir = "asc")

        assertEquals(listOf(1, 3, 2), sortedIds)
        assertEquals(listOf("1", "3", "2"), selectedIds)
        verify(entityManager, never())
            .createQuery("select count(*) from TemporalThing x", java.lang.Long::class.java)
    }

    private data class TemporalThing(
        val id: Int,
        val createdAt: LocalDateTime,
    )
}
