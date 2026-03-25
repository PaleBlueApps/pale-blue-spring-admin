package com.paleblueapps.springadmin.annotation

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AdminComputedField(
    val name: String = "",
)
