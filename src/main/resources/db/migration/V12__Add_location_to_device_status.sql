-- Add location fields to device_status table for customer location tracking
ALTER TABLE device_status 
ADD COLUMN IF NOT EXISTS latitude DECIMAL(10, 8),
ADD COLUMN IF NOT EXISTS longitude DECIMAL(11, 8);

-- Add index for location queries
CREATE INDEX IF NOT EXISTS idx_device_status_location ON device_status(latitude, longitude) 
WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

-- Add comments
COMMENT ON COLUMN device_status.latitude IS 'Device latitude coordinate';
COMMENT ON COLUMN device_status.longitude IS 'Device longitude coordinate';
