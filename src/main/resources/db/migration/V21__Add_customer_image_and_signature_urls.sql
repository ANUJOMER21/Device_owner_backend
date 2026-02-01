ALTER TABLE customers
ADD COLUMN IF NOT EXISTS customer_image_url TEXT;

ALTER TABLE customers
ADD COLUMN IF NOT EXISTS signature_image_url TEXT;

COMMENT ON COLUMN customers.customer_image_url IS 'Local upload path or URL for customer photo';
COMMENT ON COLUMN customers.signature_image_url IS 'Local upload path or URL for customer signature';

