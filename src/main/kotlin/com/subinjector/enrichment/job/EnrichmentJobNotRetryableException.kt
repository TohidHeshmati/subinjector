package com.subinjector.enrichment.job

class EnrichmentJobNotRetryableException(jobId: String) :
    RuntimeException("Enrichment job $jobId is not completed and cannot be retried")
