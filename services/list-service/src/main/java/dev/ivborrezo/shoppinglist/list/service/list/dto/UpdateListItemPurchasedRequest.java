package dev.ivborrezo.shoppinglist.list.service.list.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Petición de cambio del estado de compra de un ítem.
 *
 * <p>{@code purchased} es obligatorio: el wrapper {@link Boolean} permite distinguir su ausencia,
 * que {@code @NotNull} traduce a {@code VALIDATION_FAILED} en la capa de controller.
 */
public record UpdateListItemPurchasedRequest(@NotNull Boolean purchased) {}
