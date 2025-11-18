package com.paleblueapps.springadminexample.example.entity

import jakarta.persistence.*

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

    val age: Int
)
