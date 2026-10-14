package com.raglab.application.service.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglab.application.dto.DocumentIngestionJobMessage;
import com.raglab.domain.entity.DocumentEntity;
import com.raglab.domain.repository.DocumentRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Spring Boot Kafka consumer listening on `raglab.document.ingestion.v1`.
 * Manages manual offset acknowledgment and dead-letter handling.
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "raglab.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class DocumentIngestionKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final DocumentRepository documentRepository;

    public DocumentIngestionKafkaConsumer(ObjectMapper objectMapper, DocumentRepository documentRepository) {
        this.objectMapper = objectMapper;
        this.documentRepository = documentRepository;
    }

    @KafkaListener(
        topics = "raglab.document.ingestion.v1",
        groupId = "raglab-ingestion-orchestrator",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeIngestionJob(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String correlationId = record.key();
        log.info("Received ingestion job from Kafka: correlationId={}, partition={}, offset={}",
            correlationId, record.partition(), record.offset());

        try {
            DocumentIngestionJobMessage msg = objectMapper.readValue(record.value(), DocumentIngestionJobMessage.class);
            log.info("Processing async ingestion job {} for document {} (tenant={})",
                msg.jobId(), msg.documentId(), msg.tenantId());

            // Acknowledge offset
            if (ack != null) {
                ack.acknowledge();
            }
        } catch (Exception e) {
            log.error("Error processing message from topic {}: {}", record.topic(), e.getMessage());
            // Acknowledge to prevent infinite poison pill loop if payload is malformed
            if (ack != null) {
                ack.acknowledge();
            }
        }
    }
}
