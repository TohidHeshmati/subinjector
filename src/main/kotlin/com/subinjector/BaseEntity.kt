package com.subinjector

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity : Persistable<UUID> {
    @Id
    @Column(name = "id")
    private val _id: UUID = UUID.randomUUID()

    @Transient
    private var persisted = false

    override fun getId(): UUID = _id
    override fun isNew() = !persisted

    @PostPersist
    @PostLoad
    fun markPersisted() {
        persisted = true
    }
}
