-- DEV ONLY seed data (repeatable, safe to re-run). Not loaded in production:
-- override spring.flyway.locations to classpath:db/migration there.
-- DLR-001 id matches the org_id of Keycloak test user dealer1.
INSERT INTO dealer (id, dealer_code, name, contact_person, email, phone, address, is_active, created_by, updated_by) VALUES
                                                                                                                         ('22222222-2222-2222-2222-222222222222', 'DLR-001', 'Demo Dealer One', 'Anita Rao',  'dealer.one@example.com', '+91-9000000001', 'Whitefield, Bengaluru', TRUE,  'seed', 'seed'),
                                                                                                                         ('33333333-3333-3333-3333-333333333333', 'DLR-002', 'Demo Dealer Two', 'Vikram Shah','dealer.two@example.com', '+91-9000000002', 'Andheri, Mumbai',       TRUE,  'seed', 'seed'),
                                                                                                                         ('44444444-4444-4444-4444-444444444444', 'DLR-003', 'Inactive Dealer', 'Test Person', 'inactive@example.com',   '+91-9000000003', 'Test address',          FALSE, 'seed', 'seed')
    ON CONFLICT DO NOTHING;
