package com.subinjector.subtitle

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SubtitleDocumentRepository : JpaRepository<SubtitleDocument, UUID>
