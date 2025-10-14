package com.paleblueapps.springadmin.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "spring.data.admin")
data class AdminProperties(
    var enabled: Boolean = true,
    var basePath: String = "/admin",
    var ui: Ui = Ui(),
    var pagination: Pagination = Pagination(),
    var features: Features = Features(),
) {
    init {
        require(basePath.isNotBlank()) { "basePath must not be blank" }
        require(basePath.startsWith("/")) { "basePath must start with /" }
    }
    data class Ui(var title: String = "Admin", var brandLogo: String? = null)
    data class Pagination(var defaultSize: Int = 25, var maxSize: Int = 200)
    data class Features(
        var createEnabled: Boolean = true,
        var updateEnabled: Boolean = true,
        var deleteEnabled: Boolean = true,
    )
}
