package com.subinjector.subtitle

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SubtitleCueRepository : JpaRepository<SubtitleCueEntity, UUID> {
    fun countByDocument(document: SubtitleDocument): Long

    fun findAllByDocumentOrderBySequenceNumber(document: SubtitleDocument): List<SubtitleCueEntity>

    @Query("""
        SELECT c FROM SubtitleCueEntity c
        WHERE c.document = :document AND c.sequenceNumber < :sequenceNumber
        ORDER BY c.sequenceNumber DESC
    """)
    fun findPreviousCue(
        @Param("document") document: SubtitleDocument,
        @Param("sequenceNumber") sequenceNumber: Int,
    ): List<SubtitleCueEntity>

    @Query("""
        SELECT c FROM SubtitleCueEntity c
        WHERE c.document = :document AND c.sequenceNumber > :sequenceNumber
        ORDER BY c.sequenceNumber ASC
    """)
    fun findNextCue(
        @Param("document") document: SubtitleDocument,
        @Param("sequenceNumber") sequenceNumber: Int,
    ): List<SubtitleCueEntity>
}
