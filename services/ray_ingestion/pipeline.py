import os
import time
import logging
from typing import List, Dict, Any, Optional
import ray
from services.ray_ingestion.models import IngestionTaskRequest, IngestionTaskResponse
from services.ray_ingestion.worker import process_ingestion_task

logger = logging.getLogger("RayIngestionService")

# Define Ray remote task with retry controls and resource limits (Phase 5)
@ray.remote(max_retries=3, retry_exceptions=True, num_cpus=1)
def ray_parse_and_chunk_task(req_dict: Dict[str, Any]) -> Dict[str, Any]:
    req = IngestionTaskRequest(**req_dict)
    resp = process_ingestion_task(req)
    return resp.model_dump()

class RayIngestionPipeline:
    """
    Coordinates distributed document ingestion on Ray cluster with bounded concurrency.
    """

    def __init__(self, max_concurrent_tasks: int = 4):
        self.max_concurrent_tasks = max_concurrent_tasks
        if not ray.is_initialized():
            ray.init(ignore_reinit_error=True, include_dashboard=False)
            logger.info("Initialized local Ray runtime for distributed ingestion pipeline.")

    def submit_task(self, req: IngestionTaskRequest) -> ray.ObjectRef:
        """
        Dispatches a single document parsing and chunking task to Ray.
        """
        return ray_parse_and_chunk_task.remote(req.model_dump())

    def process_batch(self, requests: List[IngestionTaskRequest]) -> List[IngestionTaskResponse]:
        """
        Processes a batch of document requests respecting concurrency limits.
        """
        if not requests:
            return []

        object_refs = []
        for req in requests:
            ref = self.submit_task(req)
            object_refs.append(ref)

        results_raw = ray.get(object_refs)
        return [IngestionTaskResponse(**r) for r in results_raw]

    def shutdown(self):
        if ray.is_initialized():
            ray.shutdown()

