-- Add additional loan fields to customer_loan_details table
ALTER TABLE customer_loan_details 
ADD COLUMN IF NOT EXISTS product_price DECIMAL(15, 2),
ADD COLUMN IF NOT EXISTS down_payment DECIMAL(15, 2),
ADD COLUMN IF NOT EXISTS loan_amount DECIMAL(15, 2),
ADD COLUMN IF NOT EXISTS rate_of_interest DECIMAL(5, 2),
ADD COLUMN IF NOT EXISTS monthly_emi DECIMAL(10, 2),
ADD COLUMN IF NOT EXISTS emi_date DATE,
ADD COLUMN IF NOT EXISTS remark TEXT;

-- Add comments
COMMENT ON COLUMN customer_loan_details.product_price IS 'Total product price';
COMMENT ON COLUMN customer_loan_details.down_payment IS 'Down payment amount';
COMMENT ON COLUMN customer_loan_details.loan_amount IS 'Loan amount (product_price - down_payment)';
COMMENT ON COLUMN customer_loan_details.rate_of_interest IS 'Rate of interest percentage';
COMMENT ON COLUMN customer_loan_details.monthly_emi IS 'Monthly EMI amount';
COMMENT ON COLUMN customer_loan_details.emi_date IS 'EMI payment date';
COMMENT ON COLUMN customer_loan_details.remark IS 'Additional remarks';
