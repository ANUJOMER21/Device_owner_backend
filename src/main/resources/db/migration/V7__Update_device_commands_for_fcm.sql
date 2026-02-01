-- Update device_commands table to support FCM retry mechanism
ALTER TABLE device_commands 
ADD COLUMN IF NOT EXISTS expiry_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0,
ADD COLUMN IF NOT EXISTS last_retry_at TIMESTAMP WITH TIME ZONE;

-- Drop and recreate CHECK constraint to include new statuses
ALTER TABLE device_commands DROP CONSTRAINT IF EXISTS device_commands_status_check;
ALTER TABLE device_commands ADD CONSTRAINT device_commands_status_check 
CHECK (status IN ('pending', 'sent', 'executing', 'executed', 'failed', 'cancelled', 'expired'));

-- Add index for retry queries
CREATE INDEX IF NOT EXISTS idx_device_commands_retry ON device_commands(status, expiry_at, last_retry_at, retry_count) 
WHERE status IN ('pending', 'sent', 'executing');

-- Add comment
COMMENT ON COLUMN device_commands.expiry_at IS 'Command expiry time (24 hours from creation)';
COMMENT ON COLUMN device_commands.retry_count IS 'Number of FCM retry attempts';
COMMENT ON COLUMN device_commands.last_retry_at IS 'Last FCM retry timestamp';
