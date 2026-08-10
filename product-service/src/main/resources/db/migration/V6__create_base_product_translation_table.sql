-- Tabla de nombres y descripciones localizados de productos base (patrón i18n
-- Table). La FK con ON DELETE CASCADE es coherente con que las traducciones no
-- tienen identidad fuera de su producto base.
CREATE TABLE base_product_translation (
    product_id  BIGINT       NOT NULL,
    locale      VARCHAR(5)   NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    PRIMARY KEY (product_id, locale),
    CONSTRAINT fk_base_product_translation_product
        FOREIGN KEY (product_id) REFERENCES base_product (id) ON DELETE CASCADE
);
