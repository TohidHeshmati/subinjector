package com.subinjector.enrichment

class EnrichmentJobNotFoundException(id: String) : RuntimeException("Enrichment job $id was not found")
