package com.paleblueapps.springadmin.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.data.admin")
data class AdminProperties(
    val enabled: Boolean = true,
    val basePath: String = "/admin",
    val ui: Ui = Ui(),
    val pagination: Pagination = Pagination(),
) {
    init {
        require(basePath.isNotBlank()) { "basePath must not be blank" }
        require(basePath.startsWith("/")) { "basePath must start with /" }
    }

    data class Ui(val title: String = "Paleblue Spring Admin")
    data class Pagination(val defaultSize: Int = 25, val maxSize: Int = 200)
}
