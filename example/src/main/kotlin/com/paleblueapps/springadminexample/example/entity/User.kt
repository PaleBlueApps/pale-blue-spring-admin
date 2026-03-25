package com.paleblueapps.springadminexample.example.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,
    @Column(nullable = false)
    val username: String,
    @Column(nullable = false, unique = true)
    val email: String,
    val age: Int,
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    val posts: List<Post> = emptyList(),
) {
    override fun toString(): String = username
}
