package com.paleblueapps.springadmin.core

import jakarta.persistence.metamodel.Attribute

data class AdminEntityDescriptor(
    val entityName: String,
    val displayName: String,
    val jpaName: String,
    val javaType: Class<*>,
    val idAttribute: String,
    val idType: Class<*>,
    val attributes: List<Attribute<*, *>>,
)
