-- Seed de 30 productos base repartidos entre las 10 categorías del seed V2,
-- con los IDs 1-30 fijados explícitamente para que las traducciones referencien
-- sin ambigüedad. Todos los productos arrancan activos.
INSERT INTO base_product (id, code, category_id, default_unit, calories, calories_per, is_active) VALUES
    -- dairy (1)
    (1,  'whole_milk',       1,  'L',    NULL, 'ML',   TRUE),
    (2,  'yogurt_natural',   1,  'UNIT', NULL, 'UNIT', TRUE),
    (3,  'cured_cheese',     1,  'G',    350,  'G',    TRUE),
    (4,  'butter',           1,  'G',    717,  'G',    TRUE),
    -- bakery (2)
    (5,  'sliced_bread',     2,  'UNIT', NULL, 'UNIT', TRUE),
    (6,  'whole_wheat_bread',2,  'UNIT', NULL, 'UNIT', TRUE),
    (7,  'croissant',        2,  'UNIT', 406,  'G',    TRUE),
    -- produce (3)
    (8,  'tomato',           3,  'KG',   NULL, 'G',    TRUE),
    (9,  'apple',            3,  'KG',   NULL, 'G',    TRUE),
    (10, 'banana',           3,  'KG',   89,   'G',    TRUE),
    (11, 'potato',           3,  'KG',   NULL, 'G',    TRUE),
    (12, 'onion',            3,  'KG',   NULL, 'G',    TRUE),
    -- meat (4)
    (13, 'chicken_breast',   4,  'KG',   165,  'G',    TRUE),
    (14, 'ground_beef',      4,  'KG',   250,  'G',    TRUE),
    (15, 'pork_chops',       4,  'KG',   242,  'G',    TRUE),
    -- fish (5)
    (16, 'salmon_fillet',    5,  'KG',   208,  'G',    TRUE),
    (17, 'canned_tuna',      5,  'G',    132,  'G',    TRUE),
    -- pantry (6)
    (18, 'white_rice',       6,  'KG',   365,  'G',    TRUE),
    (19, 'pasta',            6,  'KG',   371,  'G',    TRUE),
    (20, 'olive_oil',        6,  'L',    884,  'ML',   TRUE),
    (21, 'table_salt',       6,  'G',    NULL, 'G',    TRUE),
    -- beverages (7)
    (22, 'mineral_water',    7,  'L',    NULL, 'ML',   TRUE),
    (23, 'orange_juice',     7,  'L',    NULL, 'ML',   TRUE),
    (24, 'ground_coffee',    7,  'G',    NULL, 'G',    TRUE),
    -- frozen (8)
    (25, 'frozen_peas',      8,  'G',    78,   'G',    TRUE),
    (26, 'frozen_pizza',     8,  'UNIT', 270,  'G',    TRUE),
    -- household (9)
    (27, 'dish_soap',        9,  'ML',   NULL, 'ML',   TRUE),
    (28, 'paper_towels',     9,  'UNIT', NULL, 'UNIT', TRUE),
    -- personal_care (10)
    (29, 'shampoo',          10, 'ML',   NULL, 'ML',   TRUE),
    (30, 'toothpaste',       10, 'ML',   NULL, 'ML',   TRUE);

-- Traducciones: 30 productos × 3 idiomas (es/en/eu) = 90 filas. Algunos
-- productos incluyen descripción en los tres idiomas; el resto llevan NULL.
INSERT INTO base_product_translation (product_id, locale, name, description) VALUES
    -- whole_milk (1)
    (1, 'es', 'Leche entera', 'Leche de vaca entera, sin desnatar'),
    (1, 'en', 'Whole milk', 'Full-fat cow milk'),
    (1, 'eu', 'Esne osoa', 'Behi-esne osoa, gaingabetu gabea'),
    -- yogurt_natural (2)
    (2, 'es', 'Yogur natural', NULL),
    (2, 'en', 'Natural yogurt', NULL),
    (2, 'eu', 'Jogurt naturala', NULL),
    -- cured_cheese (3)
    (3, 'es', 'Queso curado', 'Queso de leche de oveja con maduración prolongada'),
    (3, 'en', 'Cured cheese', E'Sheep\'s milk cheese with extended aging'),
    (3, 'eu', 'Gazta ondua', 'Ardi-esne gazta, ontze luzearekin'),
    -- butter (4)
    (4, 'es', 'Mantequilla', NULL),
    (4, 'en', 'Butter', NULL),
    (4, 'eu', 'Gurina', NULL),
    -- sliced_bread (5)
    (5, 'es', 'Pan de molde', 'Pan de trigo en rebanadas, listo para consumir'),
    (5, 'en', 'Sliced bread', 'Wheat bread pre-sliced, ready to eat'),
    (5, 'eu', 'Moldeko ogia', 'Gari-ogia xerratan, kontsumitzeko prest'),
    -- whole_wheat_bread (6)
    (6, 'es', 'Pan integral', 'Pan elaborado con harina de trigo integral'),
    (6, 'en', 'Whole wheat bread', 'Bread made with whole wheat flour'),
    (6, 'eu', 'Ogi integrala', 'Gari integral irinarekin egindako ogia'),
    -- croissant (7)
    (7, 'es', 'Cruasán', NULL),
    (7, 'en', 'Croissant', NULL),
    (7, 'eu', 'Kruasana', NULL),
    -- tomato (8)
    (8, 'es', 'Tomate', NULL),
    (8, 'en', 'Tomato', NULL),
    (8, 'eu', 'Tomatea', NULL),
    -- apple (9)
    (9, 'es', 'Manzana', NULL),
    (9, 'en', 'Apple', NULL),
    (9, 'eu', 'Sagarra', NULL),
    -- banana (10)
    (10, 'es', 'Plátano', NULL),
    (10, 'en', 'Banana', NULL),
    (10, 'eu', 'Banana', NULL),
    -- potato (11)
    (11, 'es', 'Patata', NULL),
    (11, 'en', 'Potato', NULL),
    (11, 'eu', 'Patata', NULL),
    -- onion (12)
    (12, 'es', 'Cebolla', NULL),
    (12, 'en', 'Onion', NULL),
    (12, 'eu', 'Tipula', NULL),
    -- chicken_breast (13)
    (13, 'es', 'Pechuga de pollo', 'Filete de pechuga de pollo sin piel ni hueso'),
    (13, 'en', 'Chicken breast', 'Skinless boneless chicken breast fillet'),
    (13, 'eu', 'Oilasko bularra', 'Oilasko bular xerra, azalik eta hezurrik gabe'),
    -- ground_beef (14)
    (14, 'es', 'Carne picada de ternera', NULL),
    (14, 'en', 'Ground beef', NULL),
    (14, 'eu', 'Txekor haragi xehatua', NULL),
    -- pork_chops (15)
    (15, 'es', 'Chuletas de cerdo', NULL),
    (15, 'en', 'Pork chops', NULL),
    (15, 'eu', 'Txerri txuletak', NULL),
    -- salmon_fillet (16)
    (16, 'es', 'Filete de salmón', 'Filete de salmón fresco sin espinas'),
    (16, 'en', 'Salmon fillet', 'Fresh boneless salmon fillet'),
    (16, 'eu', 'Izokin xerra', 'Izokin xerra freskoa, hezurrik gabe'),
    -- canned_tuna (17)
    (17, 'es', 'Atún en lata', 'Atún claro en aceite de oliva, lata de 120 g'),
    (17, 'en', 'Canned tuna', 'Light tuna in olive oil, 120 g can'),
    (17, 'eu', 'Hegaluze lata', 'Hegaluze argia oliba-oliotan, 120 g lata'),
    -- white_rice (18)
    (18, 'es', 'Arroz blanco', NULL),
    (18, 'en', 'White rice', NULL),
    (18, 'eu', 'Arroz zuria', NULL),
    -- pasta (19)
    (19, 'es', 'Pasta', 'Pasta de sémola de trigo duro'),
    (19, 'en', 'Pasta', 'Durum wheat semolina pasta'),
    (19, 'eu', 'Pasta', 'Gari gogor semola pasta'),
    -- olive_oil (20)
    (20, 'es', 'Aceite de oliva', 'Aceite de oliva virgen extra, 1 litro'),
    (20, 'en', 'Olive oil', 'Extra virgin olive oil, 1 litre'),
    (20, 'eu', 'Oliba-olioa', 'Oliba-olio birjina extra, 1 litro'),
    -- table_salt (21)
    (21, 'es', 'Sal de mesa', NULL),
    (21, 'en', 'Table salt', NULL),
    (21, 'eu', 'Mahai-gatza', NULL),
    -- mineral_water (22)
    (22, 'es', 'Agua mineral', NULL),
    (22, 'en', 'Mineral water', NULL),
    (22, 'eu', 'Ur minerala', NULL),
    -- orange_juice (23)
    (23, 'es', 'Zumo de naranja', 'Zumo de naranja natural sin azúcares añadidos'),
    (23, 'en', 'Orange juice', 'Natural orange juice with no added sugar'),
    (23, 'eu', 'Laranja-zukua', 'Laranja-zuku naturala, azukre erantsirik gabe'),
    -- ground_coffee (24)
    (24, 'es', 'Café molido', NULL),
    (24, 'en', 'Ground coffee', NULL),
    (24, 'eu', 'Kafe ehoa', NULL),
    -- frozen_peas (25)
    (25, 'es', 'Guisantes congelados', NULL),
    (25, 'en', 'Frozen peas', NULL),
    (25, 'eu', 'Ilar izoztuak', NULL),
    -- frozen_pizza (26)
    (26, 'es', 'Pizza congelada', 'Pizza familiar de jamón y queso, 400 g'),
    (26, 'en', 'Frozen pizza', 'Family-size ham and cheese pizza, 400 g'),
    (26, 'eu', 'Pizza izoztua', 'Familia-tamainako urdaiazpiko eta gazta pizza, 400 g'),
    -- dish_soap (27)
    (27, 'es', 'Lavavajillas líquido', NULL),
    (27, 'en', 'Dish soap', NULL),
    (27, 'eu', 'Ontzi-garbigailu likidoa', NULL),
    -- paper_towels (28)
    (28, 'es', 'Papel de cocina', 'Rollo de papel de cocina de doble capa'),
    (28, 'en', 'Paper towels', 'Double-layer kitchen paper roll'),
    (28, 'eu', 'Sukalde-papera', 'Geruza bikoitzeko sukalde-paper erroilua'),
    -- shampoo (29)
    (29, 'es', 'Champú', NULL),
    (29, 'en', 'Shampoo', NULL),
    (29, 'eu', 'Xanpua', NULL),
    -- toothpaste (30)
    (30, 'es', 'Pasta de dientes', NULL),
    (30, 'en', 'Toothpaste', NULL),
    (30, 'eu', 'Hortzetako pasta', NULL);
