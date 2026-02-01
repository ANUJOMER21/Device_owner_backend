-- Create dealer payments table
CREATE TABLE IF NOT EXISTS dealer_payments (
    id BIGSERIAL PRIMARY KEY,
    dealer_id VARCHAR(50) NOT NULL,
    payment_id VARCHAR(50) UNIQUE NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    payment_status VARCHAR(20) DEFAULT 'pending' CHECK (payment_status IN ('pending', 'completed', 'failed', 'refunded')),
    transaction_id VARCHAR(100),
    payment_date TIMESTAMP WITH TIME ZONE,
    due_date DATE,
    description TEXT,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dealer_payment_dealer FOREIGN KEY (dealer_id) REFERENCES dealers(dealer_id) ON DELETE CASCADE
);

-- Add index for faster queries
CREATE INDEX IF NOT EXISTS idx_dealer_payments_dealer_id ON dealer_payments(dealer_id);
CREATE INDEX IF NOT EXISTS idx_dealer_payments_status ON dealer_payments(payment_status);
CREATE INDEX IF NOT EXISTS idx_dealer_payments_date ON dealer_payments(payment_date);

-- Add comment
COMMENT ON TABLE dealer_payments IS 'Payment records for dealers';