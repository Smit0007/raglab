import os
import json
import logging
from typing import Optional
from kafka import KafkaConsumer, KafkaProducer
from services.ray_ingestion.models import IngestionTaskRequest
from services.ray_ingestion.pipeline import RayIngestionPipeline

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("RayKafkaConsumer")

class RayKafkaIngestionConsumer:
    """
    Consumes from `raglab.document.ingestion.v1`, dispatches tasks to Ray cluster,
    and publishes generated chunks to `raglab.document.chunks.v1`.
    """

    def __init__(
        self,
        bootstrap_servers: str = "localhost:9092",
        group_id: str = "raglab-ray-ingestion-workers",
        input_topic: str = "raglab.document.ingestion.v1",
        output_topic: str = "raglab.document.chunks.v1",
        dlq_topic: str = "raglab.ingestion.dlq.v1"
    ):
        self.bootstrap_servers = bootstrap_servers
        self.group_id = group_id
        self.input_topic = input_topic
        self.output_topic = output_topic
        self.dlq_topic = dlq_topic
        self.pipeline = RayIngestionPipeline(max_concurrent_tasks=4)
        self.producer = None
        self.consumer = None

    def connect(self):
        self.producer = KafkaProducer(
            bootstrap_servers=self.bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
            retries=3
        )
        self.consumer = KafkaConsumer(
            self.input_topic,
            bootstrap_servers=self.bootstrap_servers,
            group_id=self.group_id,
            enable_auto_commit=False,
            auto_offset_reset="earliest",
            value_deserializer=lambda v: json.loads(v.decode("utf-8"))
        )

    def process_single_message(self, msg_payload: dict, correlation_key: str):
        try:
            req = IngestionTaskRequest(
                job_id=msg_payload.get("jobId", "job_default"),
                tenant_id=msg_payload["tenantId"],
                document_id=msg_payload["documentId"],
                filename=msg_payload.get("filename", "unknown"),
                content_type=msg_payload.get("contentType", "text/plain"),
                text_content=msg_payload.get("textContent", None),
                file_bytes_base64=msg_payload.get("fileBytesBase64", None)
            )

            ref = self.pipeline.submit_task(req)
            import ray
            result_dict = ray.get(ref)

            if result_dict.get("status") == "COMPLETED":
                if self.producer:
                    self.producer.send(self.output_topic, key=correlation_key, value=result_dict)
                logger.info(f"Published {result_dict.get('total_chunks')} chunks for document {req.document_id}")
            else:
                if self.producer:
                    self.producer.send(self.dlq_topic, key=correlation_key, value=result_dict)
                logger.warning(f"Ingestion job failed, routed to DLQ: {result_dict.get('error_message')}")

            return result_dict
        except Exception as e:
            logger.error(f"Error handling Kafka message: {e}")
            if self.producer:
                self.producer.send(self.dlq_topic, key=correlation_key, value={"error": str(e), "raw": msg_payload})
            raise

    def close(self):
        if self.consumer:
            self.consumer.close()
        if self.producer:
            self.producer.close()
        self.pipeline.shutdown()

