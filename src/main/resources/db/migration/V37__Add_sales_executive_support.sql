-- V37: Add Sales Executive support
-- Sales executives are sub-users of a dealer who can add customers but have restricted access

-- Create sales_executives table
CREATE TABLE IF NOT EXISTS sales_executives (
    id BIGSERIAL PRIMARY KEY,
    sales_executive_id VARCHAR(50) NOT NULL UNIQUE,
    dealer_id VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    plain_password VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    assigned_kits INT NOT NULL DEFAULT 0,
    pin_hash VARCHAR(255),
    is_pin_set BOOLEAN NOT NULL DEFAULT false,
    current_token_id VARCHAR(255),
    is_logged_in BOOLEAN NOT NULL DEFAULT false,
    last_login TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes
CREATE INDEX IF NOT EXISTS idx_se_dealer_id ON sales_executives(dealer_id);
CREATE INDEX IF NOT EXISTS idx_se_phone ON sales_executives(phone);
CREATE UNIQUE INDEX IF NOT EXISTS idx_se_dealer_phone ON sales_executives(dealer_id, phone);

-- Add sales_executive_id to customers table to track which SE added them
ALTER TABLE customers ADD COLUMN IF NOT EXISTS sales_executive_id VARCHAR(50);
CREATE INDEX IF NOT EXISTS idx_customers_se_id ON customers(sales_executive_id);

-- Add phone column to dealers if not already present (for phone-based login)
-- phone column already exists, just ensure it's searchable
CREATE INDEX IF NOT EXISTS idx_dealers_phone ON dealers(phone);
