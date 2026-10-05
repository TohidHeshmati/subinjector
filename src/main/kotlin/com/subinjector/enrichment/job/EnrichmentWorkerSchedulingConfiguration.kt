package com.subinjector.enrichment.job

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["subinjector.enrichment.worker.enabled"], havingValue = "true", matchIfMissing = true)
@EnableScheduling
class EnrichmentWorkerSchedulingConfiguration
