package com.paleblueapps.springadminexample.example.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.LocalDateTime

@MappedSuperclass
open class AuditedEntity(
    @Column(name = "created_at", nullable = false)
    open val createdAt: LocalDateTime,
)
