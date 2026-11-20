-- DEV ONLY seed data (repeatable; safe to re-run)
INSERT INTO category (id, parent_id, name, sort_order) VALUES
 ('c0000000-0000-0000-0000-000000000001', NULL, 'Seating', 1),
 ('c0000000-0000-0000-0000-000000000002', NULL, 'Desks', 2),
 ('c0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000001', 'Office Chairs', 1),
 ('c0000000-0000-0000-0000-000000000004', NULL, 'Storage', 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO product (id, sku, name, description, category_id, unit_price, availability, min_order_qty, attributes) VALUES
 ('d0000000-0000-0000-0000-000000000001', 'CHR-ERG-001', 'Ergonomic Office Chair', 'Mesh back, adjustable arms', 'c0000000-0000-0000-0000-000000000003', 4500.00, 'AVAILABLE', 10, '{"colour":"Black","material":"Mesh"}'),
 ('d0000000-0000-0000-0000-000000000002', 'DSK-STD-120', 'Standing Desk 120cm', 'Electric height adjustable', 'c0000000-0000-0000-0000-000000000002', 18500.00, 'AVAILABLE', 5, '{"widthCm":120,"colour":"Walnut"}'),
 ('d0000000-0000-0000-0000-000000000003', 'STR-CAB-004', '4-Drawer Filing Cabinet', 'Steel, lockable', 'c0000000-0000-0000-0000-000000000004', 7200.00, 'OUT_OF_STOCK', 2, '{"material":"Steel"}')
ON CONFLICT (id) DO NOTHING;
