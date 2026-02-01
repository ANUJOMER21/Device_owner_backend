-- Add FCM token field to dealers table
ALTER TABLE dealers 
ADD COLUMN IF NOT EXISTS fcm_token VARCHAR(500);

-- Add comment
COMMENT ON COLUMN dealers.fcm_token IS 'Firebase Cloud Messaging token for push notifications';