package com.paleblueapps.springadminexample.example.entity

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "boards")
class Board(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,
    val name: String,
    @OneToMany(mappedBy = "board", fetch = FetchType.LAZY)
    val entries: List<BoardEntry> = emptyList(),
) {
    override fun toString(): String = name
}
