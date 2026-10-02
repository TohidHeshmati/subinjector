package com.subinjector.subtitle

import com.subinjector.enrichment.CefrLevel
import com.subinjector.enrichment.EnrichmentJobStore
import com.subinjector.enrichment.LearningLanguage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets

@Service
class SubtitleUploadService(
    private val subtitleParser: SubtitleParser,
    private val enrichmentJobStore: EnrichmentJobStore,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun upload(
        filename: String?,
        content: ByteArray,
        learningLanguage: LearningLanguage,
        learnerLevel: CefrLevel,
    ): SubtitleUploadResponse {
        if (content.isEmpty()) throw InvalidSubtitleException("Uploaded file is empty")
        if (filename?.endsWith(".srt", ignoreCase = true) != true) {
            throw InvalidSubtitleException("Uploaded file must have an .srt filename")
        }

        val startedAt = System.nanoTime()
        val entries = subtitleParser.parse(String(content, StandardCharsets.UTF_8))
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        val firstEntry = entries.first()
        val lastEntry = entries.last()
        logger.info(
            "Parsed SRT upload: bytes={}, entries={}, sequenceRange={}..{}, timelineRange={}..{}, elapsedMs={}",
            content.size,
            entries.size,
            firstEntry.sequenceNumber,
            lastEntry.sequenceNumber,
            firstEntry.startTime,
            lastEntry.endTime,
            elapsedMillis,
        )
        val submission = enrichmentJobStore.createDocument(filename, entries, learningLanguage, learnerLevel)
        return SubtitleUploadResponse(
            documentId = submission.documentId,
            jobId = submission.jobId,
            status = submission.status,
            cueCount = entries.size,
        )
    }
}
