-- Traducciones de los nombres de las diez categorías del seed V2 en los tres
-- idiomas soportados (es/en/eu). Los category_id 1-10 coinciden con las
-- categorías insertadas en V2 por orden de aparición.
INSERT INTO category_translation (category_id, locale, name) VALUES
    -- dairy
    (1, 'es', 'Lácteos'),
    (1, 'en', 'Dairy'),
    (1, 'eu', 'Esnekiak'),
    -- bakery
    (2, 'es', 'Panadería'),
    (2, 'en', 'Bakery'),
    (2, 'eu', 'Okindegia'),
    -- produce
    (3, 'es', 'Frutas y verduras'),
    (3, 'en', 'Fruits and vegetables'),
    (3, 'eu', 'Frutak eta barazkiak'),
    -- meat
    (4, 'es', 'Carne'),
    (4, 'en', 'Meat'),
    (4, 'eu', 'Haragia'),
    -- fish
    (5, 'es', 'Pescado'),
    (5, 'en', 'Fish'),
    (5, 'eu', 'Arrainak'),
    -- pantry
    (6, 'es', 'Despensa'),
    (6, 'en', 'Pantry'),
    (6, 'eu', 'Despentsa'),
    -- beverages
    (7, 'es', 'Bebidas'),
    (7, 'en', 'Beverages'),
    (7, 'eu', 'Edariak'),
    -- frozen
    (8, 'es', 'Congelados'),
    (8, 'en', 'Frozen'),
    (8, 'eu', 'Izoztuak'),
    -- household
    (9, 'es', 'Hogar'),
    (9, 'en', 'Household'),
    (9, 'eu', 'Etxea'),
    -- personal_care
    (10, 'es', 'Higiene personal'),
    (10, 'en', 'Personal care'),
    (10, 'eu', 'Higiene pertsonala');
