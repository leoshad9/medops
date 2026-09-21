-- Adds per-user storage quota tracking for clinical file uploads (PDFs).
-- The MedOps deploy-local stack stores PDF bytes on a Docker named volume
-- backed by EC2 local storage; this table tracks how much disk each user
-- (patient) has consumed so a single user cannot exhaust the disk.
-- See UploadReportService for the reservation/release lifecycle.

CREATE SCHEMA IF NOT EXISTS files;

CREATE TABLE files.user_storage (
    user_id     uuid  NOT NULL,
    used_bytes  bigint NOT NULL DEFAULT 0,
    CONSTRAINT user_storage_pkey PRIMARY KEY (user_id),
    CONSTRAINT fk_user_storage_user
        FOREIGN KEY (user_id) REFERENCES user_management.users(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_user_storage_non_negative
        CHECK (used_bytes >= 0)
);

CREATE INDEX idx_user_storage_used_bytes ON files.user_storage (used_bytes);

-- Seed zero-usage rows for every existing user so the atomic quota
-- UPDATE in UserStorageRepository.reserveQuota works without an
-- extra INSERT round-trip per upload.
INSERT INTO files.user_storage (user_id, used_bytes)
SELECT id, 0
FROM user_management.users
ON CONFLICT (user_id) DO NOTHING;
