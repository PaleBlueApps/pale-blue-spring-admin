package com.paleblueapps.springadmin.autoconfigure

import com.paleblueapps.springadmin.core.AdminCrudService
import com.paleblueapps.springadmin.core.AdminEntityRegistry
import com.paleblueapps.springadmin.web.AdminEntityController
import com.paleblueapps.springadmin.web.AdminIndexController
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan

@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass(EntityManager::class)
@EnableConfigurationProperties(AdminProperties::class)
@ConditionalOnProperty(
    prefix = "spring.data.admin",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@ComponentScan(basePackages = ["com.paleblueapps.springadmin"])
class AdminAutoConfiguration {
    @Bean
    fun adminEntityRegistry(entityManager: EntityManager): AdminEntityRegistry = AdminEntityRegistry(entityManager)

    @Bean
    fun adminCrudService(
        entityManager: EntityManager,
        registry: AdminEntityRegistry,
    ): AdminCrudService = AdminCrudService(entityManager, registry)

    @Bean
    fun adminIndexController(
        registry: AdminEntityRegistry,
        props: AdminProperties,
    ) = AdminIndexController(registry, props)

    @Bean
    fun adminEntityController(
        crud: AdminCrudService,
        registry: AdminEntityRegistry,
        props: AdminProperties,
    ) = AdminEntityController(crud, registry, props)
}
