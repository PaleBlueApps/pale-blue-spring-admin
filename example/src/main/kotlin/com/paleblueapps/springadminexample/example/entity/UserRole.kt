package com.paleblueapps.springadminexample.example.entity

import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "user_roles",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_role", columnNames = ["user_id", "role_id"])
    ]
)
data class UserRole(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, foreignKey = ForeignKey(name = "fk_userrole_user"))
    val user: User,

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false, foreignKey = ForeignKey(name = "fk_userrole_role"))
    val role: Role
)
