-- Add document status fields to customers table
ALTER TABLE customers 
ADD COLUMN IF NOT EXISTS aadhar_status VARCHAR(20) DEFAULT 'not_submitted' CHECK (aadhar_status IN ('not_submitted', 'pending', 'completed')),
ADD COLUMN IF NOT EXISTS pan_status VARCHAR(20) DEFAULT 'not_submitted' CHECK (pan_status IN ('not_submitted', 'pending', 'completed')),
ADD COLUMN IF NOT EXISTS loan_status VARCHAR(20) DEFAULT 'not_submitted' CHECK (loan_status IN ('not_submitted', 'pending', 'completed'));

-- Create indexes for status fields
CREATE INDEX IF NOT EXISTS idx_customers_aadhar_status ON customers(aadhar_status);
CREATE INDEX IF NOT EXISTS idx_customers_pan_status ON customers(pan_status);
CREATE INDEX IF NOT EXISTS idx_customers_loan_status ON customers(loan_status);

-- Add comments
COMMENT ON COLUMN customers.aadhar_status IS 'Aadhar document submission status';
COMMENT ON COLUMN customers.pan_status IS 'PAN document submission status';
COMMENT ON COLUMN customers.loan_status IS 'Loan details submission status';
