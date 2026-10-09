import unittest
import uuid
import ray
from services.ray_ingestion.models import IngestionTaskRequest, IngestionTaskResponse
from services.ray_ingestion.worker import process_ingestion_task, compute_chunk_uuid
from services.ray_ingestion.pipeline import RayIngestionPipeline

class TestRayIngestionService(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        # Initialize Ray once for testing
        if not ray.is_initialized():
            ray.init(ignore_reinit_error=True, num_cpus=2, include_dashboard=False)

    @classmethod
    def tearDownClass(cls):
        if ray.is_initialized():
            ray.shutdown()

    def test_deterministic_chunk_id(self):
        tenant_id = str(uuid.uuid4())
        doc_id = str(uuid.uuid4())
        hash1 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        id1 = compute_chunk_uuid(tenant_id, doc_id, 0, hash1)
        id2 = compute_chunk_uuid(tenant_id, doc_id, 0, hash1)
        self.assertEqual(id1, id2, "Chunk UUID must be completely deterministic")

        id3 = compute_chunk_uuid(tenant_id, doc_id, 1, hash1)
        self.assertNotEqual(id1, id3, "Different chunk index must produce distinct chunk id")

    def test_worker_processing_success(self):
        tenant_id = str(uuid.uuid4())
        doc_id = str(uuid.uuid4())
        sample_text = "Distributed computing with Ray allows parallel execution of ingestion tasks. " * 20

        req = IngestionTaskRequest(
            job_id="test_job_1",
            tenant_id=tenant_id,
            document_id=doc_id,
            filename="ray_paper.txt",
            content_type="text/plain",
            text_content=sample_text,
            chunk_size=30,
            chunk_overlap=5
        )

        resp = process_ingestion_task(req)
        self.assertEqual(resp.status, "COMPLETED")
        self.assertGreater(resp.total_chunks, 1)
        self.assertEqual(resp.document_id, doc_id)
        self.assertEqual(resp.tenant_id, tenant_id)

        # Verify chunk 0 structure
        chunk0 = resp.chunks[0]
        self.assertEqual(chunk0.chunk_index, 0)
        self.assertTrue(len(chunk0.content) > 0)
        self.assertIsNotNone(chunk0.chunk_id)

    def test_ray_distributed_batch_execution(self):
        pipeline = RayIngestionPipeline(max_concurrent_tasks=2)
        tenant_id = str(uuid.uuid4())

        requests = []
        for i in range(4):
            requests.append(IngestionTaskRequest(
                job_id=f"batch_job_{i}",
                tenant_id=tenant_id,
                document_id=str(uuid.uuid4()),
                filename=f"doc_{i}.txt",
                content_type="text/plain",
                text_content=f"Document number {i} with distinct text payload for distributed test.",
                chunk_size=10,
                chunk_overlap=2
            ))

        results = pipeline.process_batch(requests)
        self.assertEqual(len(results), 4)
        for res in results:
            self.assertEqual(res.status, "COMPLETED")
            self.assertGreater(res.total_chunks, 0)

if __name__ == "__main__":
    unittest.main()

