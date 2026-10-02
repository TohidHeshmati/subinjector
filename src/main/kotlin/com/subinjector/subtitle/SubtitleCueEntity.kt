package com.subinjector.subtitle

import com.subinjector.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "subtitle_cue")
class SubtitleCueEntity(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    val document: SubtitleDocument,
    val sequenceNumber: Int,
    val startMs: Int,
    val endMs: Int,
    @Column(name = "original_text")
    val originalText: String,
) : BaseEntity() {
    fun toDomain() = SubtitleCue(
        sequenceNumber = sequenceNumber,
        startMs = startMs,
        endMs = endMs,
        text = originalText,
    )
}
