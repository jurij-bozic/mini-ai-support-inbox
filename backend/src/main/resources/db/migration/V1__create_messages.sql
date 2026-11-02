CREATE TABLE messages (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_name  VARCHAR(200),
    customer_email VARCHAR(320),
    content        TEXT        NOT NULL,
    category       VARCHAR(20),
    status         VARCHAR(20) NOT NULL,
    ai_draft       TEXT,
    final_reply    TEXT,
    ai_error_code  VARCHAR(50),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at    TIMESTAMPTZ,
    version        BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT messages_content_length CHECK (char_length(content) BETWEEN 1 AND 5000),
    CONSTRAINT messages_category_valid CHECK (category IN ('QUESTION', 'COMPLAINT', 'PRAISE', 'OTHER')),
    CONSTRAINT messages_status_valid CHECK (status IN ('PENDING_AI', 'DRAFT_READY', 'AI_FAILED', 'APPROVED'))
);

CREATE INDEX idx_messages_created_at ON messages (created_at DESC);
CREATE INDEX idx_messages_status ON messages (status);
