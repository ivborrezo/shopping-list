-- Tabla de favoritos de usuario. Referencia polimórfica a producto (BASE|USER)
-- sin FK física: la integridad se valida en la capa de aplicación (ADR-013).
-- created_at la escribe la capa de aplicación con Instant.now() al insertar;
-- el DEFAULT queda como red de seguridad para inserciones que no la fijen.
CREATE TABLE user_favorite_product (
    user_id      UUID        NOT NULL,
    product_id   BIGINT      NOT NULL,
    product_type VARCHAR(4)  NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_favorite_product PRIMARY KEY (user_id, product_id, product_type),
    CONSTRAINT ck_user_favorite_product_type CHECK (product_type IN ('BASE', 'USER'))
);
