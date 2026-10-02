package com.subinjector.subtitle

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SubtitleDocumentRepository : JpaRepository<SubtitleDocument, UUID> {
    @Query("""
        SELECT d.id AS documentId, d.filename AS filename, d.format AS format,
               COUNT(c) AS cueCount, d.createdAt AS createdAt
        FROM SubtitleDocument d LEFT JOIN SubtitleCueEntity c ON c.document = d
        GROUP BY d.id, d.filename, d.format, d.createdAt
        ORDER BY d.createdAt DESC
    """)
    fun findAllSummaries(): List<SubtitleDocumentSummary>

    @Query("""
        SELECT d.id AS documentId, d.filename AS filename, d.format AS format,
               COUNT(c) AS cueCount, d.createdAt AS createdAt
        FROM SubtitleDocument d LEFT JOIN SubtitleCueEntity c ON c.document = d
        WHERE d.id = :id
        GROUP BY d.id, d.filename, d.format, d.createdAt
    """)
    fun findSummaryById(@Param("id") id: UUID): SubtitleDocumentSummary?
}
