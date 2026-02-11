-- Add device_password to customers (password set by dealer via DPM on customer device)
ALTER TABLE customers ADD COLUMN IF NOT EXISTS device_password VARCHAR(100);

-- Add phone details to device_status (collected on activation from configure app)
ALTER TABLE device_status ADD COLUMN IF NOT EXISTS device_manufacturer VARCHAR(100);
ALTER TABLE device_status ADD COLUMN IF NOT EXISTS device_model VARCHAR(100);
ALTER TABLE device_status ADD COLUMN IF NOT EXISTS device_brand VARCHAR(100);
ALTER TABLE device_status ADD COLUMN IF NOT EXISTS android_version VARCHAR(20);
ALTER TABLE device_status ADD COLUMN IF NOT EXISTS sdk_version INTEGER;
ALTER TABLE device_status ADD COLUMN IF NOT EXISTS serial_number VARCHAR(100);

-- Add phone_number column to sim_details for faster lookup of phone number changes
ALTER TABLE sim_details ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);

-- Add index on sim_details phone_number
CREATE INDEX IF NOT EXISTS idx_sim_details_phone_number ON sim_details(customer_id, phone_number);

-- Add SMS secret key per customer for offline lock/unlock via SMS
ALTER TABLE customers ADD COLUMN IF NOT EXISTS sms_secret_key VARCHAR(64);
