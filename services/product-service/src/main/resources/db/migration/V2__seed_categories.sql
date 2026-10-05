-- Los public_id del seed son fijos (UUID v4) para que el catálogo sembrado
-- tenga identidad externa estable; las filas de runtime los genera la
-- aplicación (v7).
INSERT INTO category (code, public_id, is_active) VALUES
    ('dairy',         'b999fa7d-ee1b-4d2b-a15f-9aa29fc1f8bc', TRUE),
    ('bakery',        'ad2a6f2d-e687-4d68-a8e8-45ec880b1b81', TRUE),
    ('produce',       'f6193a6a-e5ff-4d08-a330-44db1bab04c2', TRUE),
    ('meat',          'd46ac11e-509b-4bbe-a875-420159358cf1', TRUE),
    ('fish',          'be36a91a-1d3c-4d4e-b3db-920d41493a04', TRUE),
    ('pantry',        'c766ee5e-26d7-4d83-8dca-451462f0fa99', TRUE),
    ('beverages',     '45ab3499-2941-4751-ae83-8088a39d2262', TRUE),
    ('frozen',        '9be399ec-59c8-48d2-baef-38f2f407878d', TRUE),
    ('household',     '969f9b69-4cec-4545-9da8-151934af23e6', TRUE),
    ('personal_care', 'd04ff632-72da-428f-b06c-ae3e5e2cbf39', TRUE);
