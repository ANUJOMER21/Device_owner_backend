-- Device Owner APK config: stores APK URL and SHA256 checksum for provisioning QR
CREATE TABLE IF NOT EXISTS device_owner_config (
    id VARCHAR(36) PRIMARY KEY,
    apk_url VARCHAR(1024) NOT NULL,
    apk_sha256_base64 VARCHAR(128) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255)
);

-- Single row config - insert default placeholder (run only if empty)
INSERT INTO device_owner_config (id, apk_url, apk_sha256_base64)
VALUES ('default', 'https://example.com/deviceowner.apk', 'PLACEHOLDER_UPDATE_VIA_ADMIN')
ON CONFLICT (id) DO NOTHING;
