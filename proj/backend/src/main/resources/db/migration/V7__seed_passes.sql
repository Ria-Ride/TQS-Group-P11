-- US03 (KAN-98): pass types, the partner hotel and demo stay codes, so passes can be bought before
-- the client manager can edit them (US14). All names and codes are fictional.
-- Fixed ids, so tests and the demo can refer to them.

INSERT INTO pass_types (id, code, name, validity_days, base_price) VALUES
    ('7a1e0000-0000-4000-8000-000000000001', 'DAY',       'Day pass',       1, 10.00),
    ('7a1e0000-0000-4000-8000-000000000003', 'MULTI_DAY', 'Multi-day pass', 3, 25.00);

INSERT INTO pass_type_vehicle_types (pass_type_id, vehicle_type) VALUES
    ('7a1e0000-0000-4000-8000-000000000001', 'E_BIKE'),
    ('7a1e0000-0000-4000-8000-000000000001', 'E_SCOOTER'),
    ('7a1e0000-0000-4000-8000-000000000003', 'E_BIKE'),
    ('7a1e0000-0000-4000-8000-000000000003', 'E_SCOOTER');

-- 10% off gives the examples of US03: 10.00 becomes 9.00 and 25.00 becomes 22.50.
INSERT INTO partner_accommodations (id, name, discount_percent) VALUES
    ('5a7e0000-0000-4000-8000-000000000001', 'Hotel Ria Azul', 10);

-- Demo stays: three for the project period, one already over and one not started yet, so the demo
-- can show a discount and both date refusals of BR9.
INSERT INTO stays (id, code, accommodation_id, check_in, check_out) VALUES
    ('57a90000-0000-4000-8000-000000000001', 'RIA-DEMO-01',
        '5a7e0000-0000-4000-8000-000000000001', DATE '2026-11-01', DATE '2027-02-28'),
    ('57a90000-0000-4000-8000-000000000002', 'RIA-DEMO-02',
        '5a7e0000-0000-4000-8000-000000000001', DATE '2026-11-01', DATE '2027-02-28'),
    ('57a90000-0000-4000-8000-000000000003', 'RIA-DEMO-03',
        '5a7e0000-0000-4000-8000-000000000001', DATE '2026-11-01', DATE '2027-02-28'),
    ('57a90000-0000-4000-8000-000000000004', 'RIA-PAST-01',
        '5a7e0000-0000-4000-8000-000000000001', DATE '2026-10-01', DATE '2026-10-05'),
    ('57a90000-0000-4000-8000-000000000005', 'RIA-SOON-01',
        '5a7e0000-0000-4000-8000-000000000001', DATE '2027-06-01', DATE '2027-06-07');
