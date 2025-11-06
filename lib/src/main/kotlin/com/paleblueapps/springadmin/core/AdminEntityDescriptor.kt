package com.paleblueapps.springadmin.core

import jakarta.persistence.metamodel.Attribute

data class AdminEntityDescriptor(
    val entityName: String,
    val displayName: String,
    val jpaName: String,
    val javaType: Class<*>,
    val idAttribute: String,
    val idType: Class<*>,
    // Attributes to display in list views (BASIC, EMBEDDED only)
    val attributes: List<Attribute<*, *>>,
    // Attributes to display in detail views (includes BASIC, EMBEDDED, and singular associations)
    val detailAttributes: List<Attribute<*, *>>,
)
