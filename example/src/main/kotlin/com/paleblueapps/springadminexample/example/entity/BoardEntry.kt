package com.paleblueapps.springadminexample.example.entity

import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "board_entries")
class BoardEntry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,
    val label: String,
    @ManyToOne
    @JoinColumn(name = "board_id", nullable = false, foreignKey = ForeignKey(name = "fk_board_entries_board"))
    val board: Board,
    createdAt: LocalDateTime,
) : AuditedEntity(createdAt) {
    override fun toString(): String = label
}
