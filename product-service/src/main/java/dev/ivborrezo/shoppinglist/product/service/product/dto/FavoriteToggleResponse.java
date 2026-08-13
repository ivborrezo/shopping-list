package dev.ivborrezo.shoppinglist.product.service.product.dto;

/**
 * DTO de respuesta del toggle de favorito.
 *
 * <p>Indica el estado del favorito tras la operación: {@code favorited} es {@code true} si el
 * producto ha quedado marcado como favorito y {@code false} si se ha desmarcado.
 */
public record FavoriteToggleResponse(boolean favorited) {}
