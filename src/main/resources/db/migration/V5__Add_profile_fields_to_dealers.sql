-- Add profile fields to dealers table
ALTER TABLE dealers 
ADD COLUMN IF NOT EXISTS city VARCHAR(100),
ADD COLUMN IF NOT EXISTS state VARCHAR(100),
ADD COLUMN IF NOT EXISTS pincode VARCHAR(10),
ADD COLUMN IF NOT EXISTS business_name VARCHAR(255),
ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(500);

-- Add comments
COMMENT ON COLUMN dealers.city IS 'City of the dealer';
COMMENT ON COLUMN dealers.state IS 'State of the dealer';
COMMENT ON COLUMN dealers.pincode IS 'Pincode/ZIP code';
COMMENT ON COLUMN dealers.business_name IS 'Business/Company name';
COMMENT ON COLUMN dealers.profile_image_url IS 'URL to dealer profile image';
