-- V2__seed_dev_data.sql
-- Seed accounts and devices for development/demo environment

INSERT INTO accounts (vpa, holder_name, balance, version, created_at, updated_at) VALUES
('alice@demo', 'Alice', 5000.00, 0, NOW(), NOW()),
('bob@demo',   'Bob',   1000.00, 0, NOW(), NOW()),
('carol@demo', 'Carol', 2500.00, 0, NOW(), NOW()),
('dave@demo',  'Dave',   500.00, 0, NOW(), NOW());

INSERT INTO devices (device_id, account_vpa, public_key_base64, device_type, status, created_at, updated_at) VALUES
('dev-alice-001', 'alice@demo', 'MCowBQYDK2VwAyEAx123aliceKeyBase64String...', 'SMARTPHONE', 'ACTIVE', NOW(), NOW()),
('dev-bob-001',   'bob@demo',   'MCowBQYDK2VwAyEAy456bobKeyBase64String...',   'SMARTPHONE', 'ACTIVE', NOW(), NOW());

INSERT INTO bridge_nodes (node_id, node_name, is_online, last_heartbeat, total_uploads, status, created_at, updated_at) VALUES
('phone-bridge', 'Gateway Bridge Node Alpha', TRUE, NOW(), 0, 'ACTIVE', NOW(), NOW());

INSERT INTO cryptographic_keys (key_id, algorithm, key_type, public_key_pem, status, created_at) VALUES
('key-server-rsa-2048', 'RSA-2048/OAEP-SHA256', 'SERVER_ENCRYPTION', '-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...', 'ACTIVE', NOW());
