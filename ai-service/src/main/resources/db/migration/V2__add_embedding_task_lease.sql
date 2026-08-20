ALTER TABLE product_embedding_tasks
    ADD COLUMN lease_expires_at TIMESTAMPTZ;

CREATE INDEX idx_pet_worker_claim
    ON product_embedding_tasks (status, next_attempt_at, lease_expires_at, id);
