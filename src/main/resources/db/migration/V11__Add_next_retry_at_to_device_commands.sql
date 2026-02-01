-- Add next_retry_at field to device_commands table for retry scheduling
ALTER TABLE device_commands 
ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITH TIME ZONE;

-- Add index for retry scheduling queries
CREATE INDEX IF NOT EXISTS idx_device_commands_next_retry ON device_commands(status, next_retry_at) 
WHERE status IN ('queued', 'pending', 'sent', 'executing');

-- Add comment
COMMENT ON COLUMN device_commands.next_retry_at IS 'Next scheduled retry time for queued commands';
