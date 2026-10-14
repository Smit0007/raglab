package com.raglab.application.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglab.application.dto.DocumentIngestionJobMessage;
import com.raglab.domain.entity.IngestionOutboxEntity;
import com.raglab.domain.repository.IngestionOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Transactional outbox relay: polls unpublished events from ingestion_outbox
 * and emits them to Kafka topic `raglab.document.ingestion.v1`.
 */
@Service
public class IngestionOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(IngestionOutboxRelay.class);
    public static final String TOPIC_DOCUMENT_INGESTION = "raglab.document.ingestion.v1";

    private final IngestionOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public IngestionOutboxRelay(
        IngestionOutboxRepository outboxRepository,
        org.springframework.beans.factory.ObjectProvider<KafkaTemplate<String, Object>> kafkaTemplateProvider,
        ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${raglab.outbox.poll-interval-ms:500}")
    @Transactional
    public void relayUnpublishedEvents() {
        List<IngestionOutboxEntity> pending = outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();
        if (pending.isEmpty()) return;

        for (IngestionOutboxEntity record : pending) {
            try {
                String key = record.getTenantId().toString();
                String payloadJson = objectMapper.writeValueAsString(record.getPayload());

                if (kafkaTemplate != null) {
                    kafkaTemplate.send(TOPIC_DOCUMENT_INGESTION, key, payloadJson);
                }

                record.setPublished(true);
                outboxRepository.save(record);
                log.info("Relayed outbox event id={} for tenant={}", record.getId(), record.getTenantId());
            } catch (Exception e) {
                log.warn("Failed to publish outbox event id={}: {}", record.getId(), e.getMessage());
                // Leave published = false to retry on next tick
                break;
            }
        }
    }
}
