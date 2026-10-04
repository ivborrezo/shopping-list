-- Identificador externo estable de las entidades direccionables. El id interno
-- BIGINT sigue siendo la PK; public_id es la identidad que cruza la frontera
-- (ADR-016). Sin DEFAULT ni trigger: las filas nuevas lo genera la aplicación
-- (UUID v7); las filas históricas se rellenan aquí con gen_random_uuid() (v4),
-- válido como identidad aunque sin semántica temporal.
ALTER TABLE category ADD COLUMN public_id UUID;
UPDATE category SET public_id = gen_random_uuid();
ALTER TABLE category ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE category ADD CONSTRAINT uk_category_public_id UNIQUE (public_id);

ALTER TABLE base_product ADD COLUMN public_id UUID;
UPDATE base_product SET public_id = gen_random_uuid();
ALTER TABLE base_product ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE base_product ADD CONSTRAINT uk_base_product_public_id UNIQUE (public_id);

ALTER TABLE user_product ADD COLUMN public_id UUID;
UPDATE user_product SET public_id = gen_random_uuid();
ALTER TABLE user_product ALTER COLUMN public_id SET NOT NULL;
ALTER TABLE user_product ADD CONSTRAINT uk_user_product_public_id UNIQUE (public_id);