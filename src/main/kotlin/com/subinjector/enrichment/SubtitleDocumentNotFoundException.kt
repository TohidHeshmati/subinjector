package com.subinjector.enrichment

class SubtitleDocumentNotFoundException(id: String) : RuntimeException("Subtitle document $id was not found")
