-- Supports the case-insensitive per-customer lookup (lower(customer_email) = lower(?)).
CREATE INDEX idx_messages_customer_email ON messages (lower(customer_email)) WHERE customer_email IS NOT NULL;
