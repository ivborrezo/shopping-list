-- Tabla de nombres localizados de categoría (patrón i18n Table). La clave
-- primaria compuesta (category_id, locale) garantiza a nivel de base de datos
-- que cada categoría tiene como mucho una traducción por idioma. El borrado
-- en cascada al eliminar la categoría evita huérfanos, coherente con que las
-- traducciones no tienen identidad propia fuera de su categoría.
CREATE TABLE category_translation (
    category_id BIGINT       NOT NULL,
    locale      VARCHAR(5)   NOT NULL,
    name        VARCHAR(128) NOT NULL,
    PRIMARY KEY (category_id, locale),
    CONSTRAINT fk_category_translation_category
        FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE CASCADE
);
