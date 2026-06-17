-- ============================================================
--  Laulain Luxe Rentals — V1 Initial Schema
--  Flyway migration: V1__initial_schema.sql
-- ============================================================

-- ---- EXTENSIONS ----
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";   -- for ILIKE search on item names

-- ============================================================
--  CUSTOMERS
-- ============================================================
CREATE TABLE customers (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cognito_sub         VARCHAR(255) UNIQUE,            -- Cognito user sub (links to Cognito pool)
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    email               VARCHAR(255) NOT NULL UNIQUE,
    phone               VARCHAR(20),
    address_line1       VARCHAR(255),
    address_line2       VARCHAR(100),
    city                VARCHAR(100),
    state               VARCHAR(50),
    zip_code            VARCHAR(10),
    notes               TEXT,                           -- Admin notes about customer
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_cognito_sub ON customers(cognito_sub);

-- ============================================================
--  ITEM CATEGORIES
-- ============================================================
CREATE TABLE item_categories (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    sort_order  INT DEFAULT 0,
    active      BOOLEAN DEFAULT TRUE
);

INSERT INTO item_categories (name, description, sort_order) VALUES
    ('Chafing Dishes',    'Full chafing dish sets and accessories',         1),
    ('Serving Equipment', 'Trays, tongs, spoons, and serving accessories',  2),
    ('Table Linens',      'Tablecloths, napkins, and overlays',             3),
    ('Beverage Service',  'Drink dispensers, coffee urns, and accessories', 4),
    ('Decor',             'Centerpieces, candelabras, and decorative items', 5),
    ('Packages',          'Bundled packages for complete event setups',     6);

-- ============================================================
--  ITEMS (Rental Catalog)
-- ============================================================
CREATE TABLE items (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    category_id         UUID NOT NULL REFERENCES item_categories(id),
    name                VARCHAR(200) NOT NULL,
    slug                VARCHAR(200) NOT NULL UNIQUE,   -- URL-friendly identifier
    description         TEXT,
    price_per_day       NUMERIC(10, 2) NOT NULL,
    deposit_amount      NUMERIC(10, 2),                 -- Override deposit if needed
    quantity_in_stock   INT NOT NULL DEFAULT 1,
    min_rental_days     INT DEFAULT 1,
    weight_lbs          NUMERIC(6, 2),
    dimensions          VARCHAR(100),                    -- e.g. "24"W x 16"D x 8"H"
    color               VARCHAR(50),
    material            VARCHAR(100),
    care_instructions   TEXT,
    active              BOOLEAN DEFAULT TRUE,
    featured            BOOLEAN DEFAULT FALSE,           -- Show on homepage
    sort_order          INT DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_items_category ON items(category_id);
CREATE INDEX idx_items_active ON items(active);
CREATE INDEX idx_items_slug ON items(slug);
CREATE INDEX idx_items_name_trgm ON items USING GIN(name gin_trgm_ops);

-- ============================================================
--  ITEM IMAGES
-- ============================================================
CREATE TABLE item_images (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    item_id     UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    s3_key      VARCHAR(500) NOT NULL,                  -- S3 object key
    s3_url      VARCHAR(1000),                          -- Public or presigned URL
    alt_text    VARCHAR(255),
    is_primary  BOOLEAN DEFAULT FALSE,
    sort_order  INT DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_item_images_item ON item_images(item_id);

-- ============================================================
--  PACKAGES (Bundled Items)
-- ============================================================
CREATE TABLE packages (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(200) NOT NULL,
    slug            VARCHAR(200) NOT NULL UNIQUE,
    description     TEXT,
    price_per_day   NUMERIC(10, 2) NOT NULL,
    discount_pct    NUMERIC(5, 2) DEFAULT 0,            -- % discount vs. individual pricing
    active          BOOLEAN DEFAULT TRUE,
    featured        BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE package_items (
    package_id  UUID NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    item_id     UUID NOT NULL REFERENCES items(id),
    quantity    INT NOT NULL DEFAULT 1,
    PRIMARY KEY (package_id, item_id)
);

-- ============================================================
--  BOOKINGS
-- ============================================================
CREATE TYPE booking_status AS ENUM (
    'PENDING',          -- Customer submitted, awaiting admin review
    'QUOTE_SENT',       -- Quote generated and emailed
    'CONFIRMED',        -- Deposit paid, booking locked in
    'IN_PROGRESS',      -- Event day — items out on rental
    'COMPLETED',        -- Items returned
    'CANCELLED',        -- Booking cancelled
    'NO_SHOW'           -- Customer no-show
);

CREATE TABLE bookings (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_number          VARCHAR(20) NOT NULL UNIQUE,    -- e.g. LLR-2024-0001
    customer_id             UUID NOT NULL REFERENCES customers(id),
    status                  booking_status NOT NULL DEFAULT 'PENDING',

    -- Event Details
    event_date              DATE NOT NULL,
    event_start_time        TIME,
    event_end_time          TIME,
    event_type              VARCHAR(100),                   -- Wedding, Corporate, Birthday, etc.
    event_venue             VARCHAR(255),
    guest_count             INT,

    -- Delivery Info
    delivery_address_line1  VARCHAR(255),
    delivery_address_line2  VARCHAR(100),
    delivery_city           VARCHAR(100),
    delivery_state          VARCHAR(50),
    delivery_zip            VARCHAR(10),
    delivery_fee            NUMERIC(10, 2) DEFAULT 0,
    requires_setup          BOOLEAN DEFAULT FALSE,
    setup_fee               NUMERIC(10, 2) DEFAULT 0,

    -- Pricing
    subtotal                NUMERIC(10, 2),
    tax_amount              NUMERIC(10, 2),
    total_amount            NUMERIC(10, 2),
    deposit_amount          NUMERIC(10, 2),
    balance_due             NUMERIC(10, 2),

    -- Admin
    admin_notes             TEXT,
    customer_notes          TEXT,
    cancellation_reason     TEXT,

    -- Timestamps
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    confirmed_at            TIMESTAMP WITH TIME ZONE,
    cancelled_at            TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_bookings_customer ON bookings(customer_id);
CREATE INDEX idx_bookings_event_date ON bookings(event_date);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_number ON bookings(booking_number);

-- ============================================================
--  BOOKING LINE ITEMS
-- ============================================================
CREATE TABLE booking_items (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id      UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    item_id         UUID REFERENCES items(id),
    package_id      UUID REFERENCES packages(id),
    item_name       VARCHAR(200) NOT NULL,               -- Snapshot at time of booking
    quantity        INT NOT NULL DEFAULT 1,
    unit_price      NUMERIC(10, 2) NOT NULL,
    line_total      NUMERIC(10, 2) NOT NULL,
    rental_days     INT NOT NULL DEFAULT 1
);

CREATE INDEX idx_booking_items_booking ON booking_items(booking_id);

-- ============================================================
--  AVAILABILITY BLOCKS
--  Tracks which items are unavailable on which dates
--  Populated when a booking is CONFIRMED
-- ============================================================
CREATE TABLE availability_blocks (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    item_id     UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    booking_id  UUID REFERENCES bookings(id) ON DELETE CASCADE,
    block_date  DATE NOT NULL,
    quantity    INT NOT NULL DEFAULT 1,                  -- How many units blocked
    reason      VARCHAR(50) DEFAULT 'BOOKING',           -- BOOKING, MAINTENANCE, HOLD
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_availability_item_date ON availability_blocks(item_id, block_date);
CREATE UNIQUE INDEX idx_availability_unique ON availability_blocks(item_id, booking_id, block_date);

-- ============================================================
--  QUOTES
-- ============================================================
CREATE TYPE quote_status AS ENUM (
    'DRAFT',
    'SENT',
    'VIEWED',
    'ACCEPTED',
    'DECLINED',
    'EXPIRED'
);

CREATE TABLE quotes (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id      UUID NOT NULL REFERENCES bookings(id),
    quote_number    VARCHAR(20) NOT NULL UNIQUE,          -- e.g. LLR-Q-2024-0001
    status          quote_status NOT NULL DEFAULT 'DRAFT',
    subtotal        NUMERIC(10, 2) NOT NULL,
    tax_amount      NUMERIC(10, 2) NOT NULL,
    delivery_fee    NUMERIC(10, 2) DEFAULT 0,
    setup_fee       NUMERIC(10, 2) DEFAULT 0,
    total_amount    NUMERIC(10, 2) NOT NULL,
    deposit_amount  NUMERIC(10, 2) NOT NULL,
    pdf_s3_key      VARCHAR(500),
    notes           TEXT,
    valid_until     DATE NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE,
    viewed_at       TIMESTAMP WITH TIME ZONE,
    accepted_at     TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_quotes_booking ON quotes(booking_id);
CREATE INDEX idx_quotes_status ON quotes(status);

-- ============================================================
--  CONTRACTS
-- ============================================================
CREATE TYPE contract_status AS ENUM (
    'PENDING_SIGNATURE',
    'SIGNED',
    'VOIDED'
);

CREATE TABLE contracts (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id          UUID NOT NULL REFERENCES bookings(id),
    quote_id            UUID NOT NULL REFERENCES quotes(id),
    contract_number     VARCHAR(20) NOT NULL UNIQUE,
    status              contract_status NOT NULL DEFAULT 'PENDING_SIGNATURE',
    docusign_envelope_id VARCHAR(255),
    pdf_s3_key          VARCHAR(500),
    signed_pdf_s3_key   VARCHAR(500),
    customer_ip         VARCHAR(45),
    signed_at           TIMESTAMP WITH TIME ZONE,
    voided_at           TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_contracts_booking ON contracts(booking_id);
CREATE INDEX idx_contracts_envelope ON contracts(docusign_envelope_id);

-- ============================================================
--  PAYMENTS
-- ============================================================
CREATE TYPE payment_type AS ENUM ('DEPOSIT', 'BALANCE', 'REFUND', 'DAMAGE');
CREATE TYPE payment_status AS ENUM ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED');

CREATE TABLE payments (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    booking_id              UUID NOT NULL REFERENCES bookings(id),
    type                    payment_type NOT NULL,
    status                  payment_status NOT NULL DEFAULT 'PENDING',
    amount                  NUMERIC(10, 2) NOT NULL,
    stripe_payment_intent_id VARCHAR(255),
    stripe_charge_id        VARCHAR(255),
    description             TEXT,
    failure_reason          TEXT,
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_payments_booking ON payments(booking_id);
CREATE INDEX idx_payments_stripe ON payments(stripe_payment_intent_id);

-- ============================================================
--  AUDIT LOG
-- ============================================================
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,           -- 'BOOKING', 'PAYMENT', 'CONTRACT', etc.
    entity_id   UUID NOT NULL,
    action      VARCHAR(100) NOT NULL,
    performed_by VARCHAR(255),                  -- Email or system
    old_value   JSONB,
    new_value   JSONB,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_created ON audit_log(created_at);

-- ============================================================
--  SEQUENCE for human-readable IDs
-- ============================================================
CREATE SEQUENCE booking_number_seq START 1;
CREATE SEQUENCE quote_number_seq START 1;
CREATE SEQUENCE contract_number_seq START 1;
