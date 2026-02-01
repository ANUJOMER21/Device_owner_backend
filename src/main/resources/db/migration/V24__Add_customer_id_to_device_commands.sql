-- Add customer_id to device_commands table to link commands with customers
ALTER TABLE device_commands 
ADD COLUMN IF NOT EXISTS customer_id VARCHAR(50);

-- Add index for faster queries by customer_id
CREATE INDEX IF NOT EXISTS idx_device_commands_customer_id ON device_commands(customer_id);

-- Add foreign key constraint
ALTER TABLE device_commands
ADD CONSTRAINT fk_device_command_customer 
FOREIGN KEY (customer_id) REFERENCES customers(customer_id) ON DELETE SET NULL;

-- Update existing commands to have customer_id based on device_id
UPDATE device_commands dc
SET customer_id = ds.customer_id
FROM device_status ds
WHERE dc.device_id = ds.device_id
AND dc.customer_id IS NULL;

COMMENT ON COLUMN device_commands.customer_id IS 'Customer ID linked to this command for easier querying by dealers';
