-- OSON MARKET — DATABASE ARCHITECTURE V2 (SINGLE OWNER, MULTI-BRANCH, UZ/RU)
-- Engine: PostgreSQL 16+ | Backend: Spring Boot | PK: UUID | Money: NUMERIC(19,2)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Seed reference languages (uz, ru) & currency (UZS) after creating languages/currencies tables.
-- Supported languages lookup table (seed: uz, ru).
CREATE TABLE languages (
  code                       VARCHAR(8) PRIMARY KEY,
  enabled                    BOOLEAN,
  is_default                 BOOLEAN
);

-- Supported currencies (e.g. UZS).
CREATE TABLE currencies (
  code                       VARCHAR(8) PRIMARY KEY,
  symbol                     VARCHAR(16),
  decimal_places             SMALLINT,
  enabled                    BOOLEAN
);

-- Central media registry for avatars, products, variants, categories, brands, and reviews.
CREATE TABLE media_files (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  storage_key                VARCHAR(512) UNIQUE,
  mime_type                  VARCHAR(120),
  size_bytes                 BIGINT,
  width                      INT,
  height                     INT,
  created_at                 TIMESTAMPTZ
);

-- Global key-value JSONB configuration store.
CREATE TABLE system_settings (
  key                        VARCHAR(120) PRIMARY KEY,
  value                      JSONB,
  updated_at                 TIMESTAMPTZ
);

-- Core user account. Public registration ALWAYS creates USER. ADMIN provisioned through protected bootstrap only.
CREATE TABLE users (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email                      VARCHAR(255) NOT NULL UNIQUE,
  password_hash              VARCHAR(255) NOT NULL,
  role                       VARCHAR(16) NOT NULL CHECK (role IN ('USER','ADMIN')),
  status                     VARCHAR(32) NOT NULL,
  preferred_language_code    VARCHAR(8) REFERENCES languages(code),
  email_verified_at          TIMESTAMPTZ,
  created_at                 TIMESTAMPTZ NOT NULL,
  updated_at                 TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_users_preferred_language_code ON users (preferred_language_code);

-- 1:1 user personal profile and avatar reference.
CREATE TABLE user_profiles (
  user_id                    UUID PRIMARY KEY DEFAULT gen_random_uuid() REFERENCES users(id),
  first_name                 VARCHAR(100),
  last_name                  VARCHAR(100),
  phone                      VARCHAR(32),
  avatar_media_id            UUID REFERENCES media_files(id)
);
CREATE INDEX idx_user_profiles_avatar_media_id ON user_profiles (avatar_media_id);

-- Saved customer delivery addresses.
CREATE TABLE user_addresses (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                    UUID REFERENCES users(id),
  recipient_name             VARCHAR(150),
  phone                      VARCHAR(32),
  country_code               VARCHAR(4),
  region                     VARCHAR(120),
  city                       VARCHAR(120),
  district                   VARCHAR(120),
  street                     VARCHAR(200),
  building                   VARCHAR(64),
  apartment                  VARCHAR(64),
  postal_code                VARCHAR(32),
  is_default                 BOOLEAN DEFAULT false
);
CREATE INDEX idx_user_addresses_user_id ON user_addresses (user_id);

-- Hashed verification codes for signup, reset, and email confirmation.
CREATE TABLE auth_verification_codes (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                    UUID REFERENCES users(id),
  email                      VARCHAR(255),
  purpose                    VARCHAR(64),
  code_hash                  VARCHAR(255),
  attempts                   INT DEFAULT 0,
  expires_at                 TIMESTAMPTZ,
  consumed_at                TIMESTAMPTZ
);
CREATE INDEX idx_auth_verification_codes_user_id ON auth_verification_codes (user_id);

-- Tracked client device fingerprints and revocation status.
CREATE TABLE user_devices (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                    UUID REFERENCES users(id),
  fingerprint_hash           VARCHAR(255),
  last_seen_at               TIMESTAMPTZ,
  revoked_at                 TIMESTAMPTZ
);
CREATE INDEX idx_user_devices_user_id ON user_devices (user_id);

-- Hashed refresh tokens optionally bound to user devices.
CREATE TABLE refresh_tokens (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                    UUID REFERENCES users(id),
  device_id                  UUID REFERENCES user_devices(id),
  token_hash                 VARCHAR(255) UNIQUE,
  expires_at                 TIMESTAMPTZ,
  revoked_at                 TIMESTAMPTZ
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_device_id ON refresh_tokens (device_id);

-- System-wide audit log capturing entity before/after JSONB diffs.
CREATE TABLE audit_logs (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id              UUID REFERENCES users(id),
  entity_type                VARCHAR(100),
  entity_id                  UUID,
  action                     VARCHAR(64),
  old_values                 JSONB,
  new_values                 JSONB,
  created_at                 TIMESTAMPTZ
);
CREATE INDEX idx_audit_logs_actor_user_id ON audit_logs (actor_user_id);

-- Single business entity owning all branches.
CREATE TABLE stores (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code                       VARCHAR(64) UNIQUE,
  legal_name                 VARCHAR(255),
  tax_id                     VARCHAR(64),
  support_phone              VARCHAR(32),
  support_email              VARCHAR(255),
  active                     BOOLEAN DEFAULT true
);

-- Unlimited store branches belonging to the single store. Partial unique index enforces one main branch.
CREATE TABLE branches (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id                   UUID REFERENCES stores(id),
  code                       VARCHAR(64) NOT NULL UNIQUE,
  address_line               VARCHAR(255),
  country_code               VARCHAR(4),
  region                     VARCHAR(120),
  city                       VARCHAR(120),
  district                   VARCHAR(120),
  latitude                   NUMERIC(10,7),
  longitude                  NUMERIC(10,7),
  phone                      VARCHAR(32),
  email                      VARCHAR(255),
  status                     VARCHAR(32),
  pickup_enabled             BOOLEAN,
  delivery_enabled           BOOLEAN,
  is_main                    BOOLEAN,
  created_at                 TIMESTAMPTZ,
  updated_at                 TIMESTAMPTZ
);
CREATE INDEX idx_branches_city_status ON branches (city, status);
CREATE UNIQUE INDEX uq_branches_single_main ON branches (store_id) WHERE is_main = true;

-- Localized branch name and description (uz/ru).
CREATE TABLE branch_translations (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  branch_id                  UUID REFERENCES branches(id),
  language_code              VARCHAR(8) REFERENCES languages(code),
  name                       VARCHAR(200) NOT NULL,
  description                TEXT,
  UNIQUE (branch_id, language_code)
);
CREATE INDEX idx_branch_translations_branch_id ON branch_translations (branch_id);
CREATE INDEX idx_branch_translations_language_code ON branch_translations (language_code);

-- Branch operating hours per day of week (1..7) and interval.
CREATE TABLE branch_working_hours (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  branch_id                  UUID REFERENCES branches(id),
  day_of_week                SMALLINT CHECK (day_of_week BETWEEN 1 AND 7),
  opens_at                   TIME,
  closes_at                  TIME,
  is_closed                  BOOLEAN,
  interval_no                SMALLINT,
  UNIQUE (branch_id, day_of_week, interval_no)
);
CREATE INDEX idx_branch_working_hours_branch_id ON branch_working_hours (branch_id);

-- Scheduled temporary branch closures and reasons.
CREATE TABLE branch_closures (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  branch_id                  UUID REFERENCES branches(id),
  starts_at                  TIMESTAMPTZ,
  ends_at                    TIMESTAMPTZ,
  reason                     TEXT
);
CREATE INDEX idx_branch_closures_branch_id ON branch_closures (branch_id);

-- Multiple warehouses per branch; one product catalog shared by every branch.
CREATE TABLE warehouses (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  branch_id                  UUID REFERENCES branches(id),
  code                       VARCHAR(64) NOT NULL UNIQUE,
  name                       VARCHAR(200),
  active                     BOOLEAN DEFAULT true
);
CREATE INDEX idx_warehouses_branch_id ON warehouses (branch_id);

-- Recursive category tree via parent_id. Parent cycles must be prevented.
CREATE TABLE categories (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  parent_id                  UUID REFERENCES categories(id),
  slug                       VARCHAR(160) UNIQUE,
  image_media_id             UUID REFERENCES media_files(id),
  sort_order                 INT,
  active                     BOOLEAN
);
CREATE INDEX idx_categories_parent_id ON categories (parent_id);
CREATE INDEX idx_categories_image_media_id ON categories (image_media_id);

-- Localized category names and SEO metadata (uz/ru).
CREATE TABLE category_translations (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  category_id                UUID REFERENCES categories(id),
  language_code              VARCHAR(8) REFERENCES languages(code),
  name                       VARCHAR(200) NOT NULL,
  description                TEXT,
  seo_title                  VARCHAR(255),
  seo_description            TEXT,
  UNIQUE (category_id, language_code)
);
CREATE INDEX idx_category_translations_category_id ON category_translations (category_id);
CREATE INDEX idx_category_translations_language_code ON category_translations (language_code);

-- Product brands and logo media references.
CREATE TABLE brands (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug                       VARCHAR(120) UNIQUE,
  name                       VARCHAR(180),
  logo_media_id              UUID REFERENCES media_files(id),
  active                     BOOLEAN
);
CREATE INDEX idx_brands_logo_media_id ON brands (logo_media_id);

-- Shared product catalog entry across all store branches.
CREATE TABLE products (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  brand_id                   UUID REFERENCES brands(id),
  slug                       VARCHAR(200) UNIQUE,
  status                     VARCHAR(32),
  product_kind               VARCHAR(32),
  warranty_months            INT,
  published_at               TIMESTAMPTZ,
  created_at                 TIMESTAMPTZ,
  updated_at                 TIMESTAMPTZ
);
CREATE INDEX idx_products_status ON products (status);
CREATE INDEX idx_products_brand_id ON products (brand_id);

-- Localized product titles, descriptions, and SEO metadata (uz/ru).
CREATE TABLE product_translations (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id                 UUID REFERENCES products(id),
  language_code              VARCHAR(8) REFERENCES languages(code),
  title                      VARCHAR(255) NOT NULL,
  short_description          TEXT,
  description                TEXT,
  seo_title                  VARCHAR(255),
  seo_description            TEXT,
  UNIQUE (product_id, language_code)
);
CREATE INDEX idx_product_translations_product_id ON product_translations (product_id);
CREATE INDEX idx_product_translations_language_code ON product_translations (language_code);

-- Many-to-many join between products and categories.
CREATE TABLE product_categories (
  product_id                 UUID REFERENCES products(id),
  category_id                UUID REFERENCES categories(id),
  is_primary                 BOOLEAN,
  PRIMARY KEY (product_id, category_id)
);
CREATE INDEX idx_product_categories_product_id ON product_categories (product_id);
CREATE INDEX idx_product_categories_category_id ON product_categories (category_id);

-- Product-level gallery images with localized alt texts.
CREATE TABLE product_images (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id                 UUID REFERENCES products(id),
  media_id                   UUID REFERENCES media_files(id),
  sort_order                 INT,
  alt_uz                     VARCHAR(255),
  alt_ru                     VARCHAR(255),
  is_primary                 BOOLEAN
);
CREATE INDEX idx_product_images_product_id ON product_images (product_id);
CREATE INDEX idx_product_images_media_id ON product_images (media_id);

-- Product manuals, certificates, and downloadable documents.
CREATE TABLE product_documents (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id                 UUID REFERENCES products(id),
  media_id                   UUID REFERENCES media_files(id),
  document_kind              VARCHAR(64)
);
CREATE INDEX idx_product_documents_product_id ON product_documents (product_id);
CREATE INDEX idx_product_documents_media_id ON product_documents (media_id);

-- Universal product attributes and variant axes.
CREATE TABLE attributes (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code                       VARCHAR(64) NOT NULL UNIQUE,
  value_type                 VARCHAR(32),
  is_variant_axis            BOOLEAN,
  is_filterable              BOOLEAN,
  unit_code                  VARCHAR(32)
);

-- Localized attribute names (uz/ru).
CREATE TABLE attribute_translations (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  attribute_id               UUID REFERENCES attributes(id),
  language_code              VARCHAR(8) REFERENCES languages(code),
  name                       VARCHAR(160) NOT NULL,
  UNIQUE (attribute_id, language_code)
);
CREATE INDEX idx_attribute_translations_attribute_id ON attribute_translations (attribute_id);
CREATE INDEX idx_attribute_translations_language_code ON attribute_translations (language_code);

-- Predefined selectable options for an attribute.
CREATE TABLE attribute_options (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  attribute_id               UUID REFERENCES attributes(id),
  code                       VARCHAR(64),
  sort_order                 INT,
  UNIQUE (attribute_id, code)
);
CREATE INDEX idx_attribute_options_attribute_id ON attribute_options (attribute_id);

-- Localized option labels (uz/ru).
CREATE TABLE attribute_option_translations (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  option_id                  UUID REFERENCES attribute_options(id),
  language_code              VARCHAR(8) REFERENCES languages(code),
  label                      VARCHAR(160) NOT NULL,
  UNIQUE (option_id, language_code)
);
CREATE INDEX idx_attribute_option_translations_option_id ON attribute_option_translations (option_id);
CREATE INDEX idx_attribute_option_translations_language_code ON attribute_option_translations (language_code);

-- Category-to-attribute schema bindings and variant axis allowance.
CREATE TABLE category_attributes (
  category_id                UUID REFERENCES categories(id),
  attribute_id               UUID REFERENCES attributes(id),
  required                   BOOLEAN,
  variant_axis_allowed       BOOLEAN,
  sort_order                 INT,
  PRIMARY KEY (category_id, attribute_id)
);
CREATE INDEX idx_category_attributes_category_id ON category_attributes (category_id);
CREATE INDEX idx_category_attributes_attribute_id ON category_attributes (attribute_id);

-- Typed attribute values assigned to products.
CREATE TABLE product_attributes (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id                 UUID REFERENCES products(id),
  attribute_id               UUID REFERENCES attributes(id),
  option_id                  UUID REFERENCES attribute_options(id),
  value_text                 TEXT,
  value_number               NUMERIC(19,4),
  value_boolean              BOOLEAN,
  value_date                 DATE
);
CREATE INDEX idx_product_attributes_product_id ON product_attributes (product_id);
CREATE INDEX idx_product_attributes_attribute_id ON product_attributes (attribute_id);
CREATE INDEX idx_product_attributes_option_id ON product_attributes (option_id);

-- Sellable SKU unit (formerly product_types). Even simple products have exactly one default variant.
CREATE TABLE product_variants (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id                 UUID REFERENCES products(id),
  sku                        VARCHAR(100) NOT NULL UNIQUE,
  barcode                    VARCHAR(100) UNIQUE,
  weight_grams               INT,
  length_mm                  INT,
  width_mm                   INT,
  height_mm                  INT,
  status                     VARCHAR(32),
  is_default                 BOOLEAN,
  created_at                 TIMESTAMPTZ,
  updated_at                 TIMESTAMPTZ
);
CREATE INDEX idx_product_variants_product_id ON product_variants (product_id);

-- Option combination defining a product variant.
CREATE TABLE product_variant_options (
  variant_id                 UUID REFERENCES product_variants(id),
  attribute_id               UUID REFERENCES attributes(id),
  option_id                  UUID REFERENCES attribute_options(id),
  PRIMARY KEY (variant_id, attribute_id)
);
CREATE INDEX idx_product_variant_options_variant_id ON product_variant_options (variant_id);
CREATE INDEX idx_product_variant_options_attribute_id ON product_variant_options (attribute_id);
CREATE INDEX idx_product_variant_options_option_id ON product_variant_options (option_id);

-- Variant-specific imagery (formerly product_type_images).
CREATE TABLE product_variant_images (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  variant_id                 UUID REFERENCES product_variants(id),
  media_id                   UUID REFERENCES media_files(id),
  sort_order                 INT,
  is_primary                 BOOLEAN
);
CREATE INDEX idx_product_variant_images_variant_id ON product_variant_images (variant_id);
CREATE INDEX idx_product_variant_images_media_id ON product_variant_images (media_id);

-- Regular and sale prices per currency with validity window.
CREATE TABLE variant_prices (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  variant_id                 UUID REFERENCES product_variants(id),
  currency_code              VARCHAR(8) REFERENCES currencies(code),
  regular_price              NUMERIC(19,2),
  sale_price                 NUMERIC(19,2),
  valid_from                 TIMESTAMPTZ,
  valid_to                   TIMESTAMPTZ
);
CREATE INDEX idx_variant_prices_variant_id ON variant_prices (variant_id);
CREATE INDEX idx_variant_prices_currency_code ON variant_prices (currency_code);

-- Per-warehouse SKU stock levels with optimistic locking and atomic reserve checks.
CREATE TABLE inventory_stock (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  warehouse_id               UUID REFERENCES warehouses(id),
  variant_id                 UUID REFERENCES product_variants(id),
  quantity_on_hand           INT NOT NULL,
  quantity_reserved          INT NOT NULL DEFAULT 0,
  reorder_level              INT DEFAULT 0,
  version                    BIGINT DEFAULT 0,
  UNIQUE (warehouse_id, variant_id),
  CHECK (quantity_on_hand >= 0 AND quantity_reserved >= 0 AND quantity_reserved <= quantity_on_hand)
);
CREATE INDEX idx_inventory_stock_variant_id ON inventory_stock (variant_id);
CREATE INDEX idx_inventory_stock_warehouse_id ON inventory_stock (warehouse_id);

-- Append-only stock movement ledger. Never hard-delete.
CREATE TABLE stock_movements (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  warehouse_id               UUID REFERENCES warehouses(id),
  variant_id                 UUID REFERENCES product_variants(id),
  movement_type              VARCHAR(32),
  quantity_delta             INT,
  reference_type             VARCHAR(64),
  reference_id               UUID,
  actor_user_id              UUID REFERENCES users(id),
  created_at                 TIMESTAMPTZ
);
CREATE INDEX idx_stock_movements_warehouse_id ON stock_movements (warehouse_id);
CREATE INDEX idx_stock_movements_variant_id ON stock_movements (variant_id);
CREATE INDEX idx_stock_movements_actor_user_id ON stock_movements (actor_user_id);

-- Warehouse-to-warehouse stock transfer header.
CREATE TABLE stock_transfers (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_warehouse_id        UUID REFERENCES warehouses(id),
  destination_warehouse_id   UUID REFERENCES warehouses(id),
  status                     VARCHAR(32),
  requested_by               UUID REFERENCES users(id),
  approved_by                UUID REFERENCES users(id),
  created_at                 TIMESTAMPTZ,
  CHECK (source_warehouse_id <> destination_warehouse_id)
);
CREATE INDEX idx_stock_transfers_source_warehouse_id ON stock_transfers (source_warehouse_id);
CREATE INDEX idx_stock_transfers_destination_warehouse_id ON stock_transfers (destination_warehouse_id);
CREATE INDEX idx_stock_transfers_requested_by ON stock_transfers (requested_by);
CREATE INDEX idx_stock_transfers_approved_by ON stock_transfers (approved_by);

-- Line items within a stock transfer.
CREATE TABLE stock_transfer_items (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  transfer_id                UUID REFERENCES stock_transfers(id),
  variant_id                 UUID REFERENCES product_variants(id),
  quantity                   INT CHECK (quantity > 0)
);
CREATE INDEX idx_stock_transfer_items_transfer_id ON stock_transfer_items (transfer_id);
CREATE INDEX idx_stock_transfer_items_variant_id ON stock_transfer_items (variant_id);

-- One persistent shopping cart per user.
CREATE TABLE carts (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                    UUID UNIQUE REFERENCES users(id),
  updated_at                 TIMESTAMPTZ
);
CREATE INDEX idx_carts_user_id ON carts (user_id);

-- Variant quantities in a user cart.
CREATE TABLE cart_items (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cart_id                    UUID REFERENCES carts(id),
  variant_id                 UUID REFERENCES product_variants(id),
  quantity                   INT CHECK (quantity > 0),
  UNIQUE (cart_id, variant_id)
);
CREATE INDEX idx_cart_items_cart_id ON cart_items (cart_id);
CREATE INDEX idx_cart_items_variant_id ON cart_items (variant_id);

-- Customer order header and monetary totals.
CREATE TABLE orders (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_number               VARCHAR(64) NOT NULL UNIQUE,
  user_id                    UUID REFERENCES users(id),
  status                     VARCHAR(32),
  currency_code              VARCHAR(8) REFERENCES currencies(code),
  subtotal                   NUMERIC(19,2),
  discount_total             NUMERIC(19,2),
  shipping_total             NUMERIC(19,2),
  grand_total                NUMERIC(19,2),
  placed_at                  TIMESTAMPTZ
);
CREATE INDEX idx_orders_user_placed ON orders (user_id, placed_at);
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_currency_code ON orders (currency_code);

-- Immutable order address snapshot.
CREATE TABLE order_addresses (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id                   UUID REFERENCES orders(id),
  address_kind               VARCHAR(32),
  recipient_name             VARCHAR(150),
  phone                      VARCHAR(32),
  country_code               VARCHAR(4),
  region                     VARCHAR(120),
  city                       VARCHAR(120),
  district                   VARCHAR(120),
  street                     VARCHAR(200),
  building                   VARCHAR(64),
  apartment                  VARCHAR(64),
  postal_code                VARCHAR(32)
);
CREATE INDEX idx_order_addresses_order_id ON order_addresses (order_id);

-- Order line items with immutable SKU, UZ/RU title, and JSONB options snapshots.
CREATE TABLE order_items (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id                   UUID REFERENCES orders(id),
  variant_id                 UUID REFERENCES product_variants(id),
  sku_snapshot               VARCHAR(100),
  title_uz_snapshot          VARCHAR(255),
  title_ru_snapshot          VARCHAR(255),
  options_snapshot           JSONB,
  quantity                   INT,
  unit_price                 NUMERIC(19,2),
  discount_amount            NUMERIC(19,2),
  line_total                 NUMERIC(19,2)
);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_variant_id ON order_items (variant_id);

-- Immutable audit trail of order status transitions.
CREATE TABLE order_status_history (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id                   UUID REFERENCES orders(id),
  old_status                 VARCHAR(32),
  new_status                 VARCHAR(32),
  changed_by                 UUID REFERENCES users(id),
  created_at                 TIMESTAMPTZ,
  note                       TEXT
);
CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id);
CREATE INDEX idx_order_status_history_changed_by ON order_status_history (changed_by);

-- Order fulfillment split per branch/warehouse (pickup or delivery).
CREATE TABLE order_fulfillments (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id                   UUID REFERENCES orders(id),
  branch_id                  UUID REFERENCES branches(id),
  warehouse_id               UUID REFERENCES warehouses(id),
  status                     VARCHAR(32),
  fulfillment_type           VARCHAR(32),
  pickup_code_hash           VARCHAR(255),
  ready_at                   TIMESTAMPTZ,
  fulfilled_at               TIMESTAMPTZ
);
CREATE INDEX idx_order_fulfillments_branch_status ON order_fulfillments (branch_id, status);
CREATE INDEX idx_order_fulfillments_order_id ON order_fulfillments (order_id);
CREATE INDEX idx_order_fulfillments_branch_id ON order_fulfillments (branch_id);
CREATE INDEX idx_order_fulfillments_warehouse_id ON order_fulfillments (warehouse_id);

-- Quantities of order items assigned to a specific branch fulfillment.
CREATE TABLE order_fulfillment_items (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  fulfillment_id             UUID REFERENCES order_fulfillments(id),
  order_item_id              UUID REFERENCES order_items(id),
  quantity                   INT CHECK (quantity > 0)
);
CREATE INDEX idx_order_fulfillment_items_fulfillment_id ON order_fulfillment_items (fulfillment_id);
CREATE INDEX idx_order_fulfillment_items_order_item_id ON order_fulfillment_items (order_item_id);

-- Payment methods registry (VISA/HUMO/UZCARD/CASH) with admin toggle and backend-verified integration status.
CREATE TABLE payment_methods (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code                       VARCHAR(30) NOT NULL UNIQUE,
  name_uz                    VARCHAR(100) NOT NULL,
  name_ru                    VARCHAR(100) NOT NULL,
  is_enabled                 BOOLEAN NOT NULL DEFAULT false,
  integration_status         VARCHAR(30) NOT NULL DEFAULT 'NOT_CONFIGURED' CHECK (integration_status IN ('NOT_CONFIGURED','TESTING','READY')),
  sort_order                 INT NOT NULL DEFAULT 0,
  created_at                 TIMESTAMPTZ NOT NULL,
  updated_at                 TIMESTAMPTZ NOT NULL
);

-- External payment gateway providers.
CREATE TABLE payment_providers (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code                       VARCHAR(50) NOT NULL UNIQUE,
  name                       VARCHAR(100) NOT NULL,
  is_enabled                 BOOLEAN NOT NULL DEFAULT false,
  created_at                 TIMESTAMPTZ NOT NULL
);

-- Provider TEST/LIVE configuration references to secure secret vault.
CREATE TABLE payment_provider_configurations (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_id                UUID NOT NULL REFERENCES payment_providers(id),
  mode                       VARCHAR(10) NOT NULL CHECK (mode IN ('TEST','LIVE')),
  configuration_reference    VARCHAR(200),
  verified_at                TIMESTAMPTZ,
  created_at                 TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_payment_provider_configurations_provider_id ON payment_provider_configurations (provider_id);

-- Mapping between payment methods, providers, and verified configurations.
CREATE TABLE payment_method_providers (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_method_id          UUID NOT NULL REFERENCES payment_methods(id),
  payment_provider_id        UUID NOT NULL REFERENCES payment_providers(id),
  is_enabled                 BOOLEAN NOT NULL DEFAULT false,
  configuration_id           UUID REFERENCES payment_provider_configurations(id),
  UNIQUE (payment_method_id, payment_provider_id)
);
CREATE INDEX idx_payment_method_providers_payment_method_id ON payment_method_providers (payment_method_id);
CREATE INDEX idx_payment_method_providers_payment_provider_id ON payment_method_providers (payment_provider_id);
CREATE INDEX idx_payment_method_providers_configuration_id ON payment_method_providers (configuration_id);

-- Optional per-branch payment method availability (e.g., branch-specific CASH availability).
CREATE TABLE branch_payment_methods (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  branch_id                  UUID NOT NULL REFERENCES branches(id),
  payment_method_id          UUID NOT NULL REFERENCES payment_methods(id),
  is_enabled                 BOOLEAN NOT NULL DEFAULT true,
  UNIQUE (branch_id, payment_method_id)
);
CREATE INDEX idx_branch_payment_methods_branch_id ON branch_payment_methods (branch_id);
CREATE INDEX idx_branch_payment_methods_payment_method_id ON branch_payment_methods (payment_method_id);

-- Order payment records. provider_id is nullable for CASH. Snapshot selected method.
CREATE TABLE payments (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id                   UUID NOT NULL REFERENCES orders(id),
  payment_method_id          UUID NOT NULL REFERENCES payment_methods(id),
  provider_id                UUID REFERENCES payment_providers(id),
  amount                     NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  currency                   CHAR(3) NOT NULL DEFAULT 'UZS' REFERENCES currencies(code),
  status                     VARCHAR(30) NOT NULL CHECK (status IN ('PENDING','PROCESSING','SUCCEEDED','FAILED','CANCELLED','PARTIALLY_REFUNDED','REFUNDED')),
  idempotency_key            VARCHAR(100) UNIQUE,
  paid_at                    TIMESTAMPTZ,
  created_at                 TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_payments_order_id ON payments (order_id);
CREATE INDEX idx_payments_payment_method_id ON payments (payment_method_id);
CREATE INDEX idx_payments_provider_id ON payments (provider_id);
CREATE INDEX idx_payments_currency ON payments (currency);

-- Gateway transaction attempts and idempotent webhook logs.
CREATE TABLE payment_transactions (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id                 UUID NOT NULL REFERENCES payments(id),
  provider_transaction_id    VARCHAR(200),
  request_id                 VARCHAR(150) UNIQUE,
  status                     VARCHAR(30) NOT NULL,
  amount                     NUMERIC(19,2) NOT NULL,
  created_at                 TIMESTAMPTZ NOT NULL,
  UNIQUE (payment_id, provider_transaction_id)
);
CREATE INDEX idx_payment_transactions_payment_id ON payment_transactions (payment_id);

-- Immutable log of payment status transitions.
CREATE TABLE payment_status_history (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id                 UUID NOT NULL REFERENCES payments(id),
  old_status                 VARCHAR(30),
  new_status                 VARCHAR(30) NOT NULL,
  changed_by_user_id         UUID REFERENCES users(id),
  reason                     TEXT,
  created_at                 TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_payment_status_history_payment_id ON payment_status_history (payment_id);
CREATE INDEX idx_payment_status_history_changed_by_user_id ON payment_status_history (changed_by_user_id);

-- Branch cash receipt confirming physical collection of funds (1:1 with payment_id).
CREATE TABLE cash_receipts (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id                 UUID NOT NULL UNIQUE REFERENCES payments(id),
  branch_id                  UUID NOT NULL REFERENCES branches(id),
  received_by_user_id        UUID NOT NULL REFERENCES users(id),
  received_amount            NUMERIC(19,2) NOT NULL,
  received_at                TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_cash_receipts_payment_id ON cash_receipts (payment_id);
CREATE INDEX idx_cash_receipts_branch_id ON cash_receipts (branch_id);
CREATE INDEX idx_cash_receipts_received_by_user_id ON cash_receipts (received_by_user_id);

-- Refunds issued against a payment record.
CREATE TABLE refunds (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id                 UUID NOT NULL REFERENCES payments(id),
  amount                     NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  status                     VARCHAR(30) NOT NULL,
  created_at                 TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_refunds_payment_id ON refunds (payment_id);

-- Configured delivery methods.
CREATE TABLE shipping_methods (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code                       VARCHAR(64) UNIQUE,
  active                     BOOLEAN
);

-- Localized shipping method names (uz/ru).
CREATE TABLE shipping_method_translations (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  shipping_method_id         UUID REFERENCES shipping_methods(id),
  language_code              VARCHAR(8) REFERENCES languages(code),
  name                       VARCHAR(160),
  UNIQUE (shipping_method_id, language_code)
);
CREATE INDEX idx_shipping_method_translations_shipping_method_id ON shipping_method_translations (shipping_method_id);
CREATE INDEX idx_shipping_method_translations_language_code ON shipping_method_translations (language_code);

-- Geographic delivery zones.
CREATE TABLE shipping_zones (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name                       VARCHAR(160),
  country_code               VARCHAR(4),
  region                     VARCHAR(120),
  city                       VARCHAR(120)
);

-- Delivery rates per shipping method and zone.
CREATE TABLE shipping_rates (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  shipping_method_id         UUID REFERENCES shipping_methods(id),
  shipping_zone_id           UUID REFERENCES shipping_zones(id),
  amount                     NUMERIC(19,2),
  currency_code              VARCHAR(8) REFERENCES currencies(code)
);
CREATE INDEX idx_shipping_rates_shipping_method_id ON shipping_rates (shipping_method_id);
CREATE INDEX idx_shipping_rates_shipping_zone_id ON shipping_rates (shipping_zone_id);
CREATE INDEX idx_shipping_rates_currency_code ON shipping_rates (currency_code);

-- Physical shipment tracking linked to an order fulfillment.
CREATE TABLE shipments (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  fulfillment_id             UUID REFERENCES order_fulfillments(id),
  shipping_method_id         UUID REFERENCES shipping_methods(id),
  tracking_number            VARCHAR(120),
  carrier                    VARCHAR(120),
  status                     VARCHAR(32),
  shipped_at                 TIMESTAMPTZ,
  delivered_at               TIMESTAMPTZ
);
CREATE INDEX idx_shipments_fulfillment_id ON shipments (fulfillment_id);
CREATE INDEX idx_shipments_shipping_method_id ON shipments (shipping_method_id);

-- Customer return requests for delivered orders.
CREATE TABLE order_returns (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id                   UUID REFERENCES orders(id),
  user_id                    UUID REFERENCES users(id),
  reason                     TEXT,
  status                     VARCHAR(32),
  created_at                 TIMESTAMPTZ
);
CREATE INDEX idx_order_returns_order_id ON order_returns (order_id);
CREATE INDEX idx_order_returns_user_id ON order_returns (user_id);

-- Returned line items, quantities, and inspected condition.
CREATE TABLE order_return_items (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  return_id                  UUID REFERENCES order_returns(id),
  order_item_id              UUID REFERENCES order_items(id),
  quantity                   INT,
  condition                  VARCHAR(64)
);
CREATE INDEX idx_order_return_items_return_id ON order_return_items (return_id);
CREATE INDEX idx_order_return_items_order_item_id ON order_return_items (order_item_id);

-- Promo codes and time-bounded discount campaigns.
CREATE TABLE discounts (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code                       VARCHAR(64) UNIQUE,
  discount_type              VARCHAR(32),
  value                      NUMERIC(19,2),
  starts_at                  TIMESTAMPTZ,
  ends_at                    TIMESTAMPTZ,
  max_uses                   INT,
  per_user_limit             INT,
  active                     BOOLEAN
);

-- Target scope of a discount (product, category, or variant).
CREATE TABLE discount_targets (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  discount_id                UUID REFERENCES discounts(id),
  product_id                 UUID REFERENCES products(id),
  category_id                UUID REFERENCES categories(id),
  variant_id                 UUID REFERENCES product_variants(id)
);
CREATE INDEX idx_discount_targets_discount_id ON discount_targets (discount_id);
CREATE INDEX idx_discount_targets_product_id ON discount_targets (product_id);
CREATE INDEX idx_discount_targets_category_id ON discount_targets (category_id);
CREATE INDEX idx_discount_targets_variant_id ON discount_targets (variant_id);

-- Audit log of discount/promo redemptions per user and order.
CREATE TABLE promo_redemptions (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  discount_id                UUID REFERENCES discounts(id),
  user_id                    UUID REFERENCES users(id),
  order_id                   UUID REFERENCES orders(id),
  redeemed_at                TIMESTAMPTZ
);
CREATE INDEX idx_promo_redemptions_discount_id ON promo_redemptions (discount_id);
CREATE INDEX idx_promo_redemptions_user_id ON promo_redemptions (user_id);
CREATE INDEX idx_promo_redemptions_order_id ON promo_redemptions (order_id);

-- Customer wishlist / favorite products.
CREATE TABLE favorites (
  user_id                    UUID REFERENCES users(id),
  product_id                 UUID REFERENCES products(id),
  PRIMARY KEY (user_id, product_id)
);
CREATE INDEX idx_favorites_user_id ON favorites (user_id);
CREATE INDEX idx_favorites_product_id ON favorites (product_id);

-- Verified customer product reviews (rating 1..5).
CREATE TABLE product_reviews (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                    UUID REFERENCES users(id),
  product_id                 UUID REFERENCES products(id),
  order_item_id              UUID REFERENCES order_items(id),
  rating                     SMALLINT CHECK (rating BETWEEN 1 AND 5),
  comment                    TEXT,
  status                     VARCHAR(32),
  created_at                 TIMESTAMPTZ
);
CREATE INDEX idx_product_reviews_user_id ON product_reviews (user_id);
CREATE INDEX idx_product_reviews_product_id ON product_reviews (product_id);
CREATE INDEX idx_product_reviews_order_item_id ON product_reviews (order_item_id);

-- Photos attached to customer reviews.
CREATE TABLE review_images (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  review_id                  UUID REFERENCES product_reviews(id),
  media_id                   UUID REFERENCES media_files(id)
);
CREATE INDEX idx_review_images_review_id ON review_images (review_id);
CREATE INDEX idx_review_images_media_id ON review_images (media_id);

-- Customer Q&A questions on products.
CREATE TABLE product_questions (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id                 UUID REFERENCES products(id),
  user_id                    UUID REFERENCES users(id),
  question                   TEXT,
  status                     VARCHAR(32)
);
CREATE INDEX idx_product_questions_product_id ON product_questions (product_id);
CREATE INDEX idx_product_questions_user_id ON product_questions (user_id);

-- Answers to product questions.
CREATE TABLE product_answers (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  question_id                UUID REFERENCES product_questions(id),
  author_user_id             UUID REFERENCES users(id),
  answer                     TEXT
);
CREATE INDEX idx_product_answers_question_id ON product_answers (question_id);
CREATE INDEX idx_product_answers_author_user_id ON product_answers (author_user_id);

-- User notification events with structured JSONB payload.
CREATE TABLE notifications (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id                    UUID REFERENCES users(id),
  type                       VARCHAR(64),
  payload                    JSONB,
  read_at                    TIMESTAMPTZ,
  created_at                 TIMESTAMPTZ
);
CREATE INDEX idx_notifications_user_id ON notifications (user_id);

-- Localized notification title and body (uz/ru).
CREATE TABLE notification_translations (
  id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  notification_id            UUID REFERENCES notifications(id),
  language_code              VARCHAR(8) REFERENCES languages(code),
  title                      VARCHAR(255),
  body                       TEXT,
  UNIQUE (notification_id, language_code)
);
CREATE INDEX idx_notification_translations_notification_id ON notification_translations (notification_id);
CREATE INDEX idx_notification_translations_language_code ON notification_translations (language_code);
-- ============================================================================
-- SEED DATA (LANGUAGES, CURRENCY, V4 DEFAULT PAYMENT METHODS)
-- ============================================================================
INSERT INTO languages (code, enabled, is_default) VALUES ('uz', true, true), ('ru', true, false) ON CONFLICT (code) DO NOTHING;
INSERT INTO currencies (code, symbol, decimal_places, enabled) VALUES ('UZS', 'so''m', 0, true) ON CONFLICT (code) DO NOTHING;
INSERT INTO payment_methods (code, name_uz, name_ru, is_enabled, integration_status, sort_order, created_at, updated_at) VALUES
  ('CASH',   'Naqd pul',   'Наличные', true,  'READY',          1, NOW(), NOW()),
  ('UZCARD', 'Uzcard',     'Uzcard',   false, 'NOT_CONFIGURED', 2, NOW(), NOW()),
  ('HUMO',   'Humo',       'Humo',     false, 'NOT_CONFIGURED', 3, NOW(), NOW()),
  ('VISA',   'Visa karta', 'Карта Visa', false, 'NOT_CONFIGURED', 4, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;