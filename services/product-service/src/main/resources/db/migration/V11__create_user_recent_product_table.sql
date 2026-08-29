-- Tabla de productos recientes de usuario. Referencia polimórfica a producto (BASE|USER)
-- sin FK física: la integridad se valida en la capa de aplicación (ADR-013).
-- last_used_at la escribe la capa de aplicación con Instant.now() al marcar una
-- interacción; el DEFAULT queda como red de seguridad para inserciones que no la fijen.
CREATE TABLE user_recent_product (
    user_id      UUID        NOT NULL,
    product_id   BIGINT      NOT NULL,
    product_type VARCHAR(4)  NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_recent_product PRIMARY KEY (user_id, product_id, product_type),
    CONSTRAINT ck_user_recent_product_type CHECK (product_type IN ('BASE', 'USER'))
);
