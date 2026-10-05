package com.subinjector.subtitle

class SubtitleDocumentNotFoundException(id: String) : RuntimeException("Subtitle document $id was not found")
