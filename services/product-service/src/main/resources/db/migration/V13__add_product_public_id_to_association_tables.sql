-- Snapshot del identificador externo del producto referenciado por favoritos y
-- recientes (ADR-016). Se mantiene la referencia interna product_id BIGINT y se
-- añade el snapshot product_public_id UUID con el public_id del producto. La
-- columna es nullable: hay filas huérfanas
-- que apuntan a productos ya borrados (borrado físico, ADR-013) y no tienen
-- public_id que backfillear; forzar NOT NULL obligaría a borrarlas.
ALTER TABLE user_favorite_product ADD COLUMN product_public_id UUID;
UPDATE user_favorite_product f
SET product_public_id = bp.public_id
FROM base_product bp
WHERE f.product_type = 'BASE' AND f.product_id = bp.id;
UPDATE user_favorite_product f
SET product_public_id = up.public_id
FROM user_product up
WHERE f.product_type = 'USER' AND f.product_id = up.id;

ALTER TABLE user_recent_product ADD COLUMN product_public_id UUID;
UPDATE user_recent_product r
SET product_public_id = bp.public_id
FROM base_product bp
WHERE r.product_type = 'BASE' AND r.product_id = bp.id;
UPDATE user_recent_product r
SET product_public_id = up.public_id
FROM user_product up
WHERE r.product_type = 'USER' AND r.product_id = up.id;