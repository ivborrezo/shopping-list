package dev.ivborrezo.shoppinglist.list.service.list.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Petición de creación de una lista de la compra.
 *
 * <p>Los campos se validan con Bean Validation en la capa de controller vía {@code @Valid}. El
 * identificador {@code ownerId} es obligatorio y {@code name} no puede estar en blanco ni superar
 * los 128 caracteres.
 */
public record CreateListRequest(@NotNull UUID ownerId, @NotBlank @Size(max = 128) String name) {}
