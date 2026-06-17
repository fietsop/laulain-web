-- ============================================================
--  Laulain Luxe Rentals — V3 Schema Fixes
--  Flyway migration: V3__schema_fixes.sql
-- ============================================================

-- Add missing timestamp columns to item_categories
-- (ItemCategory entity has createdAt/updatedAt but V1 schema did not define them)
ALTER TABLE item_categories
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
