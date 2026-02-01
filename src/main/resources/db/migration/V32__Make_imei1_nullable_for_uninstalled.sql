-- Make imei1 nullable so we can clear it when app is uninstalled
-- Multiple uninstalled customers can have NULL imei1 (PostgreSQL UNIQUE allows multiple NULLs)
ALTER TABLE customers ALTER COLUMN imei1 DROP NOT NULL;
