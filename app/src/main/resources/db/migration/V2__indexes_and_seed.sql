-- ============================================================
--  Laulain Luxe Rentals — V2 Performance Indexes & Seed Data
--  Flyway migration: V2__indexes_and_seed.sql
-- ============================================================

-- ============================================================
--  ADDITIONAL PERFORMANCE INDEXES
-- ============================================================

-- Bookings: common admin filter patterns
CREATE INDEX IF NOT EXISTS idx_bookings_status_event_date
    ON bookings(status, event_date);

CREATE INDEX IF NOT EXISTS idx_bookings_created_at
    ON bookings(created_at DESC);

-- Availability blocks: date range scans
CREATE INDEX IF NOT EXISTS idx_availability_date_range
    ON availability_blocks(block_date, item_id);

-- Quotes: expiry scheduler
CREATE INDEX IF NOT EXISTS idx_quotes_valid_until_status
    ON quotes(valid_until, status)
    WHERE status IN ('SENT', 'VIEWED');

-- Payments: Stripe webhook lookups
-- Add stripe_session_id column first (missing from V1 schema)
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS stripe_session_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_payments_session_id
    ON payments(stripe_session_id)
    WHERE stripe_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payments_booking_status
    ON payments(booking_id, status);

-- Items: full-text search
CREATE INDEX IF NOT EXISTS idx_items_description_trgm
    ON items USING GIN(description gin_trgm_ops);

-- ============================================================
--  VIEWS — useful for reporting
-- ============================================================

-- Booking revenue summary
CREATE OR REPLACE VIEW v_booking_revenue AS
SELECT
    b.id              AS booking_id,
    b.booking_number,
    b.event_date,
    b.status,
    c.email           AS customer_email,
    c.first_name || ' ' || c.last_name AS customer_name,
    b.total_amount,
    b.deposit_amount,
    COALESCE(
        (SELECT SUM(p.amount)
         FROM payments p
         WHERE p.booking_id = b.id
           AND p.status = 'SUCCEEDED'
           AND p.type IN ('DEPOSIT','BALANCE')),
        0
    )                 AS amount_collected,
    b.total_amount - COALESCE(
        (SELECT SUM(p.amount)
         FROM payments p
         WHERE p.booking_id = b.id
           AND p.status = 'SUCCEEDED'
           AND p.type IN ('DEPOSIT','BALANCE')),
        0
    )                 AS amount_outstanding
FROM bookings b
JOIN customers c ON c.id = b.customer_id;

-- ============================================================
--  SEED DATA — default business config
-- ============================================================

-- Ensure sequences exist (idempotent)
CREATE SEQUENCE IF NOT EXISTS booking_number_seq  START 1;
CREATE SEQUENCE IF NOT EXISTS quote_number_seq    START 1;
CREATE SEQUENCE IF NOT EXISTS contract_number_seq START 1;

-- ============================================================
--  UPDATE EXISTING item_categories with icons (optional display)
-- ============================================================

ALTER TABLE item_categories
    ADD COLUMN IF NOT EXISTS icon VARCHAR(10) DEFAULT '✦';

UPDATE item_categories SET icon = '🍲' WHERE name = 'Chafing Dishes';
UPDATE item_categories SET icon = '🥄' WHERE name = 'Serving Equipment';
UPDATE item_categories SET icon = '🎀' WHERE name = 'Table Linens';
UPDATE item_categories SET icon = '🥤' WHERE name = 'Beverage Service';
UPDATE item_categories SET icon = '✨' WHERE name = 'Decor';
UPDATE item_categories SET icon = '📦' WHERE name = 'Packages';
