-- Add FCM token field to customers table for push notifications
ALTER TABLE customers 
ADD COLUMN IF NOT EXISTS fcm_token VARCHAR(500);

-- Add index for faster lookups
CREATE INDEX IF NOT EXISTS idx_customers_fcm_token ON customers(fcm_token) WHERE fcm_token IS NOT NULL;

-- Add comment
COMMENT ON COLUMN customers.fcm_token IS 'Firebase Cloud Messaging token for push notifications';
