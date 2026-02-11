-- EMI Notification tracking table
-- Stores records of all EMI reminder notifications sent to customers
-- (both automatic scheduled reminders and dealer-initiated reminders)

CREATE TABLE emi_notifications (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     VARCHAR(50)  NOT NULL,
    loan_id         VARCHAR(50)  NOT NULL,
    due_date        DATE         NOT NULL,
    notification_type VARCHAR(30) NOT NULL DEFAULT 'AUTO_REMINDER',
    sent_at         TIMESTAMPTZ,
    success         BOOLEAN      NOT NULL DEFAULT FALSE,
    error_message   TEXT,
    sent_by         VARCHAR(50),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for finding notifications by customer and due date (dedup check)
CREATE INDEX idx_emi_notifications_customer_due ON emi_notifications (customer_id, due_date, notification_type);

-- Index for finding notifications by loan
CREATE INDEX idx_emi_notifications_loan ON emi_notifications (loan_id);

-- Index for chronological queries
CREATE INDEX idx_emi_notifications_sent_at ON emi_notifications (sent_at DESC);
