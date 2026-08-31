-- V1__init_production_schema.sql
-- Production schema for Offline Payment Mesh

-- 1. Accounts
CREATE TABLE accounts (
    vpa VARCHAR(255) PRIMARY KEY,
    holder_name VARCHAR(255) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL CHECK (balance >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 2. Devices
CREATE TABLE devices (
    device_id VARCHAR(255) PRIMARY KEY,
    account_vpa VARCHAR(255) NOT NULL REFERENCES accounts(vpa),
    public_key_base64 TEXT NOT NULL,
    device_type VARCHAR(64) NOT NULL DEFAULT 'SMARTPHONE',
    status VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 3. Wallet Authorizations
CREATE TABLE wallet_authorizations (
    wallet_id VARCHAR(255) PRIMARY KEY,
    account_vpa VARCHAR(255) NOT NULL REFERENCES accounts(vpa),
    device_id VARCHAR(255) NOT NULL REFERENCES devices(device_id),
    authorized_balance NUMERIC(19, 2) NOT NULL CHECK (authorized_balance >= 0),
    max_per_tx_amount NUMERIC(19, 2) NOT NULL CHECK (max_per_tx_amount > 0),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    server_nonce VARCHAR(255) NOT NULL,
    server_key_id VARCHAR(255) NOT NULL,
    server_signature TEXT NOT NULL,
    status VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 4. Wallet Spend
CREATE TABLE wallet_spend (
    id BIGSERIAL PRIMARY KEY,
    wallet_id VARCHAR(255) NOT NULL REFERENCES wallet_authorizations(wallet_id),
    transaction_id VARCHAR(255) NOT NULL UNIQUE,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    committed_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(64) NOT NULL DEFAULT 'RESERVED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 5. Transactions
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    packet_hash VARCHAR(64) NOT NULL UNIQUE,
    sender_vpa VARCHAR(255) REFERENCES accounts(vpa),
    receiver_vpa VARCHAR(255) REFERENCES accounts(vpa),
    amount NUMERIC(19, 2) CHECK (amount > 0),
    signed_at TIMESTAMP WITH TIME ZONE,
    settled_at TIMESTAMP WITH TIME ZONE,
    bridge_node_id VARCHAR(255) NOT NULL,
    hop_count INT NOT NULL DEFAULT 0,
    state VARCHAR(64) NOT NULL,
    processing_node VARCHAR(255),
    failure_reason VARCHAR(512),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 6. Transaction Events
CREATE TABLE transaction_events (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT REFERENCES transactions(id) ON DELETE CASCADE,
    packet_hash VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    details TEXT,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 7. Bridge Nodes
CREATE TABLE bridge_nodes (
    node_id VARCHAR(255) PRIMARY KEY,
    node_name VARCHAR(255) NOT NULL,
    is_online BOOLEAN NOT NULL DEFAULT TRUE,
    last_heartbeat TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    total_uploads INT NOT NULL DEFAULT 0,
    status VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 8. Cryptographic Keys
CREATE TABLE cryptographic_keys (
    key_id VARCHAR(255) PRIMARY KEY,
    algorithm VARCHAR(64) NOT NULL,
    key_type VARCHAR(64) NOT NULL,
    public_key_pem TEXT NOT NULL,
    status VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE
);

-- 9. Audit Records
CREATE TABLE audit_records (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    payload_hash VARCHAR(64),
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Performance Indexes
CREATE INDEX idx_transactions_packet_hash ON transactions(packet_hash);
CREATE INDEX idx_transactions_state ON transactions(state);
CREATE INDEX idx_wallet_auth_account ON wallet_authorizations(account_vpa);
CREATE INDEX idx_wallet_spend_wallet ON wallet_spend(wallet_id);
CREATE INDEX idx_audit_records_entity ON audit_records(entity_id);
