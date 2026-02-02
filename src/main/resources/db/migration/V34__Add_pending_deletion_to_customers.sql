-- Flag set when admin requests delete for an installed/active customer; after device verifies REMOVE_DEVICE_OWNER we perform full cascade delete.
ALTER TABLE customers ADD COLUMN IF NOT EXISTS pending_deletion BOOLEAN NOT NULL DEFAULT FALSE;
