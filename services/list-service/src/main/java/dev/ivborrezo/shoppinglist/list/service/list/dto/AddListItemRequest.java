package dev.ivborrezo.shoppinglist.list.service.list.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Petición de alta de un producto en una lista.
 *
 * <p>Los campos se validan con Bean Validation en la capa de controller vía {@code @Valid}. {@code
 * productType} llega como {@code String} y no como el enum de dominio para que un valor no
 * soportado se traduzca a {@code INVALID_PRODUCT_TYPE} en el servicio, en lugar de a un error de
 * deserialización.
 */
public record AddListItemRequest(@NotNull UUID productId, @NotNull String productType) {}
