package dev.ivborrezo.shoppinglist.list.service.list.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición de creación de una lista de la compra.
 *
 * <p>Los campos se validan con Bean Validation en la capa de controller vía {@code @Valid}. El
 * propietario de la lista se deriva del token del usuario autenticado; {@code name} no puede estar
 * en blanco ni superar los 128 caracteres.
 */
public record CreateListRequest(@NotBlank @Size(max = 128) String name) {}
