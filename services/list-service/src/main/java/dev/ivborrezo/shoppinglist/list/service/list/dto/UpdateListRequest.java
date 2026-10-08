package dev.ivborrezo.shoppinglist.list.service.list.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Petición de renombrado parcial de una lista de la compra.
 *
 * <p>El identificador {@code ownerId} es obligatorio; {@code name} es opcional y, cuando llega,
 * tiene que respetar la longitud máxima del contrato. Si es {@code null}, la lista conserva su
 * nombre actual.
 */
public record UpdateListRequest(@NotNull UUID ownerId, @Size(max = 128) @Nullable String name) {}
