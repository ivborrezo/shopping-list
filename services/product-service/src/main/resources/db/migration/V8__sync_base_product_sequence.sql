-- Sincroniza la secuencia identity de base_product con los ids 1-30 fijados
-- explícitamente en el seed V7. Sin este ajuste, el primer INSERT con id
-- autogenerado colisionaría con la pkey en el id 1.
SELECT setval(
    pg_get_serial_sequence('base_product', 'id'),
    (SELECT MAX(id) FROM base_product)
);
