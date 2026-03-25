package com.paleblueapps.springadmin.core

import jakarta.persistence.metamodel.Attribute
import java.lang.reflect.Method

data class AdminEntityDescriptor(
    val entityName: String,
    val displayName: String,
    val jpaName: String,
    val javaType: Class<*>,
    val idAttribute: String,
    val idType: Class<*>,
    val idGenerated: Boolean,
    // Attributes to display in list views (BASIC, EMBEDDED only)
    val attributes: List<Attribute<*, *>>,
    // Attributes to display in detail views (includes BASIC, EMBEDDED, and singular associations)
    val detailAttributes: List<Attribute<*, *>>,
    // Methods mapped by field name, representing computed fields annotated with @AdminComputedField
    val computedFields: Map<String, Method> = emptyMap(),
)
