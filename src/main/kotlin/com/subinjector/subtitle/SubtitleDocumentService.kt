package com.subinjector.subtitle

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.EnrichmentJobService
import com.subinjector.enrichment.EnrichmentSubmission
import com.subinjector.enrichment.LearningLanguage
import com.subinjector.enrichment.SubtitleDocumentNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class SubtitleDocumentService(
    private val subtitleParser: SubtitleParser,
    private val documentRepository: SubtitleDocumentRepository,
    private val enrichmentJobService: EnrichmentJobService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun create(
        filename: String?,
        content: ByteArray,
        learningLanguage: LearningLanguage,
        learnerLevel: CefrLevel,
    ): EnrichmentSubmission {
        if (content.isEmpty()) throw InvalidSubtitleException("Uploaded file is empty")
        if (filename?.endsWith(".srt", ignoreCase = true) != true) {
            throw InvalidSubtitleException("Uploaded file must have an .srt filename")
        }

        val startedAt = System.nanoTime()
        val cues = subtitleParser.parse(String(content, StandardCharsets.UTF_8))
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        val firstCue = cues.first()
        val lastCue = cues.last()
        logger.info(
            "Parsed SRT upload: bytes={}, cues={}, sequenceRange={}..{}, timelineRange={}..{}, elapsedMs={}",
            content.size,
            cues.size,
            firstCue.sequenceNumber,
            lastCue.sequenceNumber,
            firstCue.startMs,
            lastCue.endMs,
            elapsedMillis,
        )
        return enrichmentJobService.createDocument(filename, cues, learningLanguage, learnerLevel)
    }

    @Transactional(readOnly = true)
    fun list(): List<SubtitleDocumentSummary> = documentRepository.findAllSummaries()

    @Transactional(readOnly = true)
    fun get(documentId: UUID): SubtitleDocumentSummary =
        documentRepository.findSummaryById(documentId)
            ?: throw SubtitleDocumentNotFoundException(documentId.toString())

    @Transactional
    fun delete(documentId: UUID) {
        if (!documentRepository.existsById(documentId)) throw SubtitleDocumentNotFoundException(documentId.toString())
        documentRepository.deleteById(documentId)
    }
}
