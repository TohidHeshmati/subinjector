package com.subinjector.enrichment.job

class EnrichmentJobNotFoundException(id: String) : RuntimeException("Enrichment job $id was not found")
