package com.paleblueapps.springadmin.web

data class AdminIndexFragmentRef(
    val templateName: String,
    val fragmentName: String,
) {
    companion object {
        fun parse(spec: String): AdminIndexFragmentRef {
            val parts = spec.split("::", limit = 2)
            require(parts.size == 2) {
                "Admin index fragment must use 'template :: fragment' format: $spec"
            }

            val templateName = parts[0].trim()
            val fragmentName = parts[1].trim()

            require(templateName.isNotBlank()) {
                "Admin index fragment template name must not be blank: $spec"
            }
            require(fragmentName.isNotBlank()) {
                "Admin index fragment name must not be blank: $spec"
            }

            return AdminIndexFragmentRef(templateName = templateName, fragmentName = fragmentName)
        }
    }
}
