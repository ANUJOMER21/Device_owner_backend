-- Update device_commands status CHECK constraint to include new statuses
-- Drop old constraint
ALTER TABLE device_commands DROP CONSTRAINT IF EXISTS device_commands_status_check;

-- Add new constraint with all statuses
ALTER TABLE device_commands 
ADD CONSTRAINT device_commands_status_check 
CHECK (status IN ('pending', 'sent', 'executing', 'executed', 'failed', 'cancelled', 'expired'));
