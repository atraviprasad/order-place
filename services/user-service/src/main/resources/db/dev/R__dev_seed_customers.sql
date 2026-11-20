-- DEV ONLY seed data (repeatable, safe to re-run). Not loaded in production.
-- CUS-001 id matches the org_id of Keycloak test user customer1.
INSERT INTO customer (id, customer_code, name, contact_person, email, phone, billing_address, delivery_address, is_active, created_by, updated_by) VALUES
 ('11111111-1111-1111-1111-111111111111', 'CUS-001', 'Demo Customer One', 'Rahul Sharma', 'facilities@customer-one.example', '+91-9100000001', 'Bengaluru, Karnataka', 'Whitefield, Bengaluru', TRUE,  'seed', 'seed'),
 ('55555555-5555-5555-5555-555555555555', 'CUS-002', 'Demo University',   'Meera Nair',   'procurement@university.example',  '+91-9100000002', 'Chennai, Tamil Nadu',   'Campus Road, Chennai',  TRUE,  'seed', 'seed'),
 ('66666666-6666-6666-6666-666666666666', 'CUS-003', 'Inactive Customer', 'Test Person',  'inactive@customer.example',       '+91-9100000003', 'Test address',          'Test address',          FALSE, 'seed', 'seed')
ON CONFLICT DO NOTHING;
